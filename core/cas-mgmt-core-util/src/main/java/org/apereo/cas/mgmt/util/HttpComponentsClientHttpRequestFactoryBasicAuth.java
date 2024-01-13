package org.apereo.cas.mgmt.util;

import lombok.val;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.net.URI;

/**
 * This is {@link HttpComponentsClientHttpRequestFactoryBasicAuth}.
 *
 * @author Misagh Moayyed
 * @since 6.3.9
 */
public class HttpComponentsClientHttpRequestFactoryBasicAuth extends HttpComponentsClientHttpRequestFactory {

    private final HttpHost host;

    public HttpComponentsClientHttpRequestFactoryBasicAuth(final HttpHost host) {
        super();
        this.host = host;
    }

    @Override
    protected HttpContext createHttpContext(final HttpMethod httpMethod, final URI uri) {
        return createHttpContext();
    }

    private HttpContext createHttpContext() {
        val authCache = new BasicAuthCache();

        val basicAuth = new BasicScheme();
        authCache.put(host, basicAuth);

        val localcontext = new BasicHttpContext();
        localcontext.setAttribute(HttpClientContext.AUTH_CACHE, authCache);
        return localcontext;
    }
}

