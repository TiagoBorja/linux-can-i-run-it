package io.github.tiagoborja.canirun.specs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limits the body of POST /api/specs to 16384 bytes. It checks Content-Length first. It also counts the
 * bytes it reads, so a chunked body (no Content-Length) cannot pass the limit.
 */
@Component
class BodySizeLimitFilter extends OncePerRequestFilter {

	static final int MAX_BYTES = 16384;

	private final HandlerExceptionResolver resolver;

	BodySizeLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && "/api/specs".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BYTES) {
			resolver.resolveException(request, response, null, new PayloadTooLargeException());
			return;
		}
		chain.doFilter(new LimitedRequest(request), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private ServletInputStream stream;

		LimitedRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				stream = new CountingStream(super.getInputStream());
			}
			return stream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String charset = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}
	}

	private static final class CountingStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private int count;

		CountingStream(ServletInputStream delegate) {
			this.delegate = delegate;
		}

		private int counted(int n) throws IOException {
			if (n > 0 && (count += n) > MAX_BYTES) {
				throw new PayloadTooLargeException();
			}
			return n;
		}

		@Override
		public int read() throws IOException {
			int b = delegate.read();
			if (b >= 0) {
				counted(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			return counted(delegate.read(buf, off, len));
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}
	}
}
