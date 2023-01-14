package com.samitkumarpatel.webfluxfileuploaddownload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@SpringBootApplication
public class WebfluxFileUploadDownloadApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebfluxFileUploadDownloadApplication.class, args);
	}

}

@Component
@Slf4j
class Routers {


	@Bean
	public RouterFunction router(Handlers handlers) {
		return RouterFunctions.route()
				.POST("/upload", accept(MediaType.MULTIPART_FORM_DATA), handlers::upload)
				.GET("/explorer", handlers::explorer)
				.GET("/download/{fileName}", handlers::download)
				.build();
	}
}

@Component
@Slf4j
class Handlers {
	@Value("${spring.application.storage.path}")
	private String basePath;

	public Mono<ServerResponse> upload(ServerRequest request) {
		return request
				.multipartData()
				.map(stringPartMultiValueMap -> stringPartMultiValueMap.get("file"))
				.flatMapMany(Flux::fromIterable)
				.cast(FilePart.class)
				.flatMap(filePart -> filePart.transferTo(Path.of(basePath).resolve(filePart.filename())))
				.onErrorResume(e -> Mono.error(e))
				.doOnError(e -> log.error(e.getMessage()))
				.doOnError(s -> log.info("Upload Successfully"))
				.then(ServerResponse.ok().body(Mono.just("SUCCESS"), String.class));
	}

	public Mono<ServerResponse> explorer(ServerRequest request) {
		//TODO this can improve
		return Mono.fromCallable(() -> {
					return Files
							.walk(Path.of(basePath), 10)
							.filter(Files::isRegularFile)
							.map(path -> path.getFileName().toString())
							.collect(Collectors.toList());
				}).onErrorResume(e -> Mono.error(e))
				.doOnError(e -> log.error(e.getMessage()))
				.doOnSuccess(r -> log.info("Explorer explore successfully"))
				.flatMap(files -> ServerResponse.ok().body(Mono.just(files), List.class));
	}

	public Mono<ServerResponse> download(ServerRequest request) {
		var fileName = request.pathVariable("fileName");
		return Mono
				.fromCallable(() -> Files.readAllBytes(Path.of(basePath).resolve(fileName)))
				.onErrorResume(e -> Mono.error(e))
				.flatMap(bytes -> Mono.just(ByteBuffer.wrap(bytes)))
				.doOnError(e -> log.error(e.getMessage()))
				.doOnSuccess(r -> log.info("Download Successful"))
				.flatMap(bytes -> {
					return ServerResponse
							.ok()
							.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s".formatted(fileName))
							.contentType(MediaType.APPLICATION_OCTET_STREAM)
							.body(Mono.just(bytes), ByteBuffer.class);
				});
	}
}