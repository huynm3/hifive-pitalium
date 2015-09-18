/*
 * Copyright (C) 2015 NS Solutions Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.htmlhifive.pitalium.core.http.handler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.htmlhifive.pitalium.core.http.PtlHttpHandler;
import com.sun.net.httpserver.HttpExchange;

/**
 * パス「/res/*」へのHTTPアクセスに対してリソースを返すHTTPハンドラー<br />
 * 「/src/main/resources/pitalium/http/res」に存在するリソースファイルを返します。
 * 
 * @author nakatani
 */
@PtlHttpHandler("/res")
public class ResourceHandler extends AbstractHttpHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ResourceHandler.class);

	private static final String RESOURCES_DIRECTORY_PATH = "pitalium/http/res/";

	private final LoadingCache<String, byte[]> cache = CacheBuilder.newBuilder().maximumSize(20L)
			.build(new CacheLoader<String, byte[]>() {
				@Override
				public byte[] load(String name) throws Exception {
					InputStream in = null;
					try {
						in = getClass().getClassLoader().getResourceAsStream(RESOURCES_DIRECTORY_PATH + name);
						if (in == null) {
							String message = String.format(Locale.US, "Resource \"%s%s\" not found.",
									RESOURCES_DIRECTORY_PATH, name);
							LOG.warn(message);
							throw new FileNotFoundException(message);
						}

						return IOUtils.toByteArray(in);
					} finally {
						if (in != null) {
							in.close();
						}
					}
				}
			});

	@Override
	protected void handle(HttpExchange httpExchange, Map<String, String> queryStrings) throws IOException {
		URI uri = httpExchange.getRequestURI();
		String[] split = uri.getPath().split("/");
		String name = split[split.length - 1];

		byte[] data;
		try {
			data = cache.get(name);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();

			// No resource file found
			if (cause instanceof FileNotFoundException) {
				sendNotFound(httpExchange);
				return;
			}

			// Unexpected error
			sendNoResponse(httpExchange, 500);
			return;
		}

		// TODO Content-Type mapping
		String contentType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(name);
		httpExchange.getResponseHeaders().set("Content-Type", contentType);
		httpExchange.sendResponseHeaders(200, data.length);
		httpExchange.getResponseBody().write(data);
		httpExchange.close();
	}

}