package io.github.galop_proxy.galop.http;

import io.github.galop_proxy.galop.configuration.HttpHeaderConfiguration;
import io.github.galop_proxy.galop.configuration.HttpHeaderRequestConfiguration;
import io.github.galop_proxy.galop.configuration.HttpHeaderResponseConfiguration;
import io.github.galop_proxy.galop.http.HttpHeaderParser.Result;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link ExchangeHandlerImpl}.
 */
public class ExchangeHandlerImplTest {

    private final static long REQUEST_TIMEOUT = 10000;
    private final static long RESPONSE_TIMEOUT = 20000;

    private HttpHeaderConfiguration configuration;
    private Result headerResult;
    private HttpHeaderParser httpHeaderParser;
    private MessageWriter messageWriter;
    private Future<Result> future;
    private ExecutorService executorService;
    private ExchangeHandlerImpl handler;

    private Socket source;
    private OutputStream sourceOutputStream;
    private Socket target;
    private Runnable callback;

    @Before
    public void setUp() throws Exception {

        configuration = mockConfiguration();
        httpHeaderParser = mockHttpHeaderParser();
        messageWriter = mock(MessageWriter.class);
        executorService = mockExecutorService();
        handler = new ExchangeHandlerImpl(configuration, httpHeaderParser, messageWriter, executorService);

        source = mockSocket();
        sourceOutputStream = new ByteArrayOutputStream();
        when(source.getOutputStream()).thenReturn(sourceOutputStream);
        target = mockSocket();
        callback = spy(Runnable.class);

    }

    private HttpHeaderConfiguration mockConfiguration() {

        final HttpHeaderRequestConfiguration requestConfiguration = mock(HttpHeaderRequestConfiguration.class);
        when(requestConfiguration.getReceiveTimeout()).thenReturn(REQUEST_TIMEOUT);

        final HttpHeaderResponseConfiguration responseConfiguration = mock(HttpHeaderResponseConfiguration.class);
        when(responseConfiguration.getReceiveTimeout()).thenReturn(RESPONSE_TIMEOUT);

        final HttpHeaderConfiguration configuration = mock(HttpHeaderConfiguration.class);
        when(configuration.getRequest()).thenReturn(requestConfiguration);
        when(configuration.getResponse()).thenReturn(responseConfiguration);
        return configuration;

    }

    private HttpHeaderParser mockHttpHeaderParser() throws IOException {
        final HttpHeaderParser httpHeaderParser = mock(HttpHeaderParser.class);
        headerResult = mock(Result.class);
        when(httpHeaderParser.parse(any(), anyBoolean(), any())).thenReturn(headerResult);
        when(httpHeaderParser.parse(any(), anyBoolean())).thenReturn(headerResult);
        return httpHeaderParser;
    }

    @SuppressWarnings("unchecked")
    private ExecutorService mockExecutorService() throws Exception {

        future = mock(Future.class);
        when(future.get(anyLong(), any())).thenReturn(headerResult);

        final ExecutorService executorService = mock(ExecutorService.class);
        doAnswer(invocation -> {
            final Callable<?> callable = (Callable<?>) invocation.getArguments()[0];
            callable.call();
            return future;
        }).when(executorService).submit((Callable<?>) any());

        return executorService;

    }

    private Socket mockSocket() throws IOException {
        final Socket socket = mock(Socket.class);
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return socket;
    }

    // Handle request:

    @Test
    public void handleRequest_withoutClientOrServerErrors_callsParserAndHandler() throws Exception {
        handler.handleRequest(source, target, callback);
        verify(httpHeaderParser).parse(any(), eq(true), same(callback));
        verify(messageWriter).writeMessage(same(headerResult), any(), any());
        verify(future).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test(expected = UnsupportedTransferEncodingException.class)
    public void handleRequest_withUnsupportedTransferEncoding_sendsStatusCode411ToClient() throws Exception {

        doThrow(UnsupportedTransferEncodingException.class).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        try {
            handler.handleRequest(source, target, callback);
        } catch (final UnsupportedTransferEncodingException ex) {
            assertHttpStatusCode(StatusCode.LENGTH_REQUIRED);
            throw ex;
        }

    }

    @Test(expected = ByteLimitExceededException.class)
    public void handleRequest_withTooLongRequestHeader_sendsStatusCode431ToClient() throws Exception {

        doThrow(ByteLimitExceededException.class).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        try {
            handler.handleRequest(source, target, callback);
        } catch (final ByteLimitExceededException ex) {
            assertHttpStatusCode(StatusCode.REQUEST_HEADER_FIELDS_TOO_LARGE);
            throw ex;
        }

    }

    @Test
    public void handleRequest_withInvalidRequestHeader_sendsStatusCode400ToClient() throws Exception {

        doThrow(Exception.class).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        try {
            handler.handleRequest(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.BAD_REQUEST);
        }

    }

    @Test
    public void handleRequest_whenInterrupted_sendsStatusCode503ToClient() throws Exception {

        doThrow(InterruptedException.class).when(future).get(anyLong(), any());

        try {
            handler.handleRequest(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.SERVICE_UNAVAILABLE);
        }

    }

    @Test
    public void handleRequest_whenReceiveRequestHeaderTimeout_sendsStatusCode408ToClient() throws Exception {

        doThrow(TimeoutException.class).when(future).get(anyLong(), any());

        try {
            handler.handleRequest(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.REQUEST_TIMEOUT);
        }

    }

    @Test
    public void handleRequest_whenAnErrorOccurredWhileSendingToServer_sendsStatusCode400ToClient() throws Exception {

        doThrow(Exception.class).when(messageWriter).writeMessage(any(), any(), any());

        try {
            handler.handleRequest(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.BAD_REQUEST);
        }

    }

    @Test
    public void handleRequest_whenAnUnexpectedErrorOccurredWhileParsingHeader_treatErrorAsInvalidRequestHeaderError()
            throws  Exception{

        doThrow(new ExecutionException(new NullPointerException())).when(future).get(anyLong(), any());

        try {
            handler.handleRequest(source, target, callback);
            fail("IOException expected.");
        } catch (final IOException ex) {
            assertHttpStatusCode(StatusCode.BAD_REQUEST);
        }

    }

    @Test(expected = IOException.class)
    public void handleRequest_whenAnErrorOccurredWhileSendingStatusCodeToClient_ignoresNewError()
            throws Exception {
        doThrow(new ExecutionException(new IOException())).when(future).get(anyLong(), any());
        when(source.getOutputStream()).thenReturn(null);
        handler.handleRequest(source, target, callback);
    }

    // Handle response:

    @Test
    public void handleResponse_withoutClientOrServerErrors_callsParserAndHandler() throws Exception {
        handler.handleResponse(source, target, callback);
        verify(httpHeaderParser).parse(any(), eq(false), any());
        verify(messageWriter).writeMessage(same(headerResult), any(), any());
        verify(callback).run();
        verify(future).get(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test
    public void handleResponse_withInvalidResponseHeader_sendsStatusCode502ToClient() throws Exception {

        doThrow(Exception.class).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.BAD_GATEWAY);
        }

    }

    @Test
    public void handleResponse_whenInterrupted_sendsStatusCode503ToClient() throws Exception {

        doThrow(InterruptedException.class).when(future).get(anyLong(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.SERVICE_UNAVAILABLE);
        }

    }

    @Test
    public void handleResponse_whenReceiveResponseHeaderTimeout_sendsStatusCode504ToClient() throws Exception {

        doThrow(TimeoutException.class).when(future).get(anyLong(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.GATEWAY_TIMEOUT);
        }

    }

    @Test
    public void handleResponse_whenAnErrorOccurredBeforeSendingToClient_sendsStatusCode502ToClient() throws Exception {

        doThrow(Exception.class).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertHttpStatusCode(StatusCode.BAD_GATEWAY);
        }

    }

    @Test
    public void handleResponse_whenAnErrorOccurredWhileSendingToClient_sendsNoStatusCodeToClient() throws Exception {

        doAnswer(invocation -> {
            final Runnable callback = (Runnable) invocation.getArguments()[2];
            callback.run();
            return null;
        }).when(httpHeaderParser).parse(any(), anyBoolean(), any());

        doThrow(Exception.class).when(messageWriter).writeMessage(any(), any(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("Exception expected.");
        } catch (final Exception ex) {
            assertTrue(sourceOutputStream.toString().isEmpty());
        }

    }

    @Test
    public void handleResponse_whenAnUnexpectedErrorOccurredWhileParsingHeader_treatErrorAsInvalidResponseHeaderError()
            throws  Exception{

        doThrow(new ExecutionException(new NullPointerException())).when(future).get(anyLong(), any());

        try {
            handler.handleResponse(source, target, callback);
            fail("IOException expected.");
        } catch (final IOException ex) {
            assertHttpStatusCode(StatusCode.BAD_GATEWAY);
        }

    }

    @Test(expected = IOException.class)
    public void handleResponse_whenAnErrorOccurredWhileSendingStatusCodeToClient_ignoresNewError()
            throws Exception {
        doThrow(new ExecutionException(new IOException())).when(future).get(anyLong(), any());
        when(source.getOutputStream()).thenReturn(null);
        handler.handleResponse(source, target, callback);
    }

    // Invalid parameters:

    @Test(expected = NullPointerException.class)
    public void constructor_withoutHttpHeaderConfiguration_throwsNullPointerException() {
        new ExchangeHandlerImpl(null, httpHeaderParser, messageWriter, executorService);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_withoutHttpHeaderParser_throwsNullPointerException() {
        new ExchangeHandlerImpl(configuration, null, messageWriter, executorService);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_withoutHttpMessageHandler_throwsNullPointerException() {
        new ExchangeHandlerImpl(configuration, httpHeaderParser, null, executorService);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_withoutExecutorService_throwsNullPointerException() {
        new ExchangeHandlerImpl(configuration, httpHeaderParser, messageWriter, null);
    }

    @Test(expected = NullPointerException.class)
    public void handleRequest_withoutSource_throwsNullPointerException() throws Exception {
        handler.handleRequest(null, target, callback);
    }

    @Test(expected = NullPointerException.class)
    public void handleRequest_withoutTarget_throwsNullPointerException() throws Exception {
        handler.handleRequest(source, null, callback);
    }

    @Test(expected = NullPointerException.class)
    public void handleRequest_withoutCallback_throwsNullPointerException() throws Exception {
        handler.handleRequest(source, target, null);
    }

    @Test(expected = NullPointerException.class)
    public void handleResponse_withoutSource_throwsNullPointerException() throws Exception {
        handler.handleResponse(null, target, callback);
    }

    @Test(expected = NullPointerException.class)
    public void handleResponse_withoutTarget_throwsNullPointerException() throws Exception {
        handler.handleResponse(source, null, callback);
    }


    @Test(expected = NullPointerException.class)
    public void handleResponse_withoutCallback_throwsNullPointerException() throws Exception {
        handler.handleResponse(source, target, null);
    }

    // Helper methods:

    private void assertHttpStatusCode(final StatusCode statusCode) {
        final String output = sourceOutputStream.toString();
        assertTrue(output.contains(Integer.toString(statusCode.getCode())));
        assertTrue(output.contains(statusCode.getReason()));
    }

}
