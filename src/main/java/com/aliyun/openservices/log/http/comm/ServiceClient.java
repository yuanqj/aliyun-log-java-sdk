package com.aliyun.openservices.log.http.comm;

import com.aliyun.openservices.log.http.client.ClientConfiguration;
import com.aliyun.openservices.log.http.client.ClientException;
import com.aliyun.openservices.log.http.client.HttpMethod;
import com.aliyun.openservices.log.http.client.ServiceException;
import com.aliyun.openservices.log.http.utils.HttpUtil;
import com.aliyun.openservices.log.util.Args;
import org.apache.http.conn.HttpClientConnectionManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public abstract class ServiceClient {

    public static class Request extends HttpMessage {
        private String uri;
        private HttpMethod method;

        public Request() {
        }

        public String getUri() {
            return this.uri;
        }

        public void setUrl(String uri) {
            this.uri = uri;
        }

        /**
         * @return the method
         */
        public HttpMethod getMethod() {
            return method;
        }

        /**
         * @param method the method to set
         */
        public void setMethod(HttpMethod method) {
            this.method = method;
        }
    }

    private static final int DEFAULT_MARK_LIMIT = 1024 * 4;

    protected ClientConfiguration config;

    protected ServiceClient(ClientConfiguration config) {
        this.config = config;
    }

    /**
     * Returns response from the service.
     *
     * @param request Request message.
     * @param charset encode charset.
     */
    public ResponseMessage sendRequest(RequestMessage request, String charset)
            throws ServiceException, ClientException {
        Args.notNull(request, "request");
        Args.notNullOrEmpty(charset, "charset");

        try {
            return sendRequestImpl(request, charset);
        } finally {
            // Close the request stream as well after the request is complete.
            try {
                request.close();
            } catch (IOException e) {
            }
        }
    }

    private ResponseMessage sendRequestImpl(RequestMessage request,
                                            String charset) throws ClientException, ServiceException {
        InputStream requestContent = request.getContent();

        if (requestContent != null && requestContent.markSupported()) {
            requestContent.mark(DEFAULT_MARK_LIMIT);
        }

        int retries = 0;
        RetryStrategy retryStrategy = config.getRetryStrategy() != null ? config.getRetryStrategy()
                : this.getDefaultRetryStrategy();

        while (true) {
            try {
                if (retries > 0) {
                    pause(retries, retryStrategy);
                    if (requestContent != null && requestContent.markSupported()) {
                        try {
                            requestContent.reset();
                        } catch (IOException ex) {
                            throw new ClientException("Failed to reset the request input stream: ", ex);
                        }
                    }
                }
                Request httpRequest = buildRequest(request, charset);
                return sendRequestCore(httpRequest, charset);
            } catch (ServiceException sex) {
                if (!shouldRetry(sex, request, retries, retryStrategy)) {
                    throw sex;
                }
            } catch (ClientException cex) {
                if (!shouldRetry(cex, request, retries, retryStrategy)) {
                    throw cex;
                }
            } catch (Exception ex) {
                throw new ClientException(ex.getMessage(), ex);
            } finally {
                retries++;
            }
        }
    }

    /**
     * Implements the core logic to send requests to Aliyun services.
     *
     * @param request
     * @param charset
     * @return response message
     * @throws Exception
     */
    protected abstract ResponseMessage sendRequestCore(Request request, String charset)
            throws Exception;

    private void pause(int retries, RetryStrategy retryStrategy) throws ClientException {
        long delay = retryStrategy.getPauseDelay(retries);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            throw new ClientException(e.getMessage(), e);
        }
    }

    private boolean shouldRetry(Exception exception, RequestMessage request,
                                int retries,
                                RetryStrategy retryStrategy) {

        if (retries >= config.getMaxErrorRetry()) {
            return false;
        }

        if (!request.isRepeatable()) {
            return false;
        }

        if (retryStrategy.shouldRetry(exception, request, retries)) {
            return true;
        }
        return false;
    }

    protected abstract RetryStrategy getDefaultRetryStrategy();

    private Request buildRequest(RequestMessage requestMessage, String charset)
            throws ClientException {
        Request request = new Request();
        request.setMethod(requestMessage.getMethod());
        request.setHeaders(requestMessage.getHeaders());
        // The header must be converted after the request is signed,
        // otherwise the signature will be incorrect.
        if (request.getHeaders() != null) {
            HttpUtil.convertHeaderCharsetToIso88591(request.getHeaders());
        }

        final String delimiter = "/";
        String uri = requestMessage.getEndpoint().toString();
        if (!uri.endsWith(delimiter)
                && (requestMessage.getResourcePath() == null ||
                !requestMessage.getResourcePath().startsWith(delimiter))) {
            uri += delimiter;
        }

        if (requestMessage.getResourcePath() != null) {
            uri += requestMessage.getResourcePath();
        }

        String paramString = HttpUtil.paramToQueryString(requestMessage.getParameters(), charset);
        /*
         * For all non-POST requests, and any POST requests that already have a
         * payload, we put the encoded params directly in the URI, otherwise,
         * we'll put them in the POST request's payload.
         */
        boolean requestHasNoPayload = requestMessage.getContent() != null;
        boolean requestIsPost = requestMessage.getMethod() == HttpMethod.POST;
        boolean putParamsInUri = !requestIsPost || requestHasNoPayload;
        if (paramString != null && putParamsInUri) {
            uri += "?" + paramString;
        }

        request.setUrl(uri);
        if (requestIsPost && requestMessage.getContent() == null && paramString != null) {
            // Put the param string to the request body if POSTing and
            // no content.
            try {
                byte[] buf = paramString.getBytes(charset);
                ByteArrayInputStream content = new ByteArrayInputStream(buf);
                request.setContent(content);
                request.setContentLength(buf.length);
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError("EncodingFailed" + e.getMessage());
            }
        } else {
            request.setContent(requestMessage.getContent());
            request.setContentLength(requestMessage.getContentLength());
        }

        return request;
    }

    public abstract void shutdown();

    public abstract HttpClientConnectionManager getConnectionManager();
}

