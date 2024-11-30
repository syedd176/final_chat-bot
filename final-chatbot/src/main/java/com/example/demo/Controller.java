package com.example.demo;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/chatbot")
public class Controller {

    @Autowired
    private OpenAISetup openAISetup;

    // Utility methods for session-based contentValue management
    private String getContentValue() {
        String contentValue = (String) RequestContextHolder.getRequestAttributes()
                .getAttribute("contentValue", RequestAttributes.SCOPE_SESSION);
        if (contentValue == null) {
            contentValue = ""; // Initialize if not present
            RequestContextHolder.getRequestAttributes()
                    .setAttribute("contentValue", contentValue, RequestAttributes.SCOPE_SESSION);
        }
        return contentValue;
    }

    private void setContentValue(String value) {
        RequestContextHolder.getRequestAttributes()
                .setAttribute("contentValue", value, RequestAttributes.SCOPE_SESSION);
    }

    // Endpoint for text-based input
    @PostMapping("/query")
    public void query(@RequestBody String userMessage, HttpServletResponse response) {
        try {
            // Get the thread ID and process the user message with Python script
            String threadId = openAISetup.getThreadId();
            openAISetup.addMessage(threadId, "user", userMessage);

            // Set the response type for streaming
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            ServletOutputStream out = response.getOutputStream();

            // Wrap ServletOutputStream with OutputStreamWriter for UTF-8 encoding
            OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            // Ensure the response is flushed and chunked immediately
            response.setHeader("Transfer-Encoding", "chunked");

            // Call Python script and stream data to the client
            openAISetup.streamAssistantResponse(threadId,"respond nicely",bufferedWriter);

            bufferedWriter.flush(); // Make sure the output is flushed after processing
        } catch (IOException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    // Endpoint for handling audio file input
    @PostMapping("/audio")
    public ResponseEntity<String> handleAudioUpload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No file uploaded.");
            }

            // Convert MultipartFile to File (if required for further processing)
            Path path = Paths.get(System.getProperty("user.dir"), "uploads", file.getOriginalFilename());
            Files.createDirectories(path.getParent()); // Ensure the directory exists
            file.transferTo(path.toFile());

            // Process the audio file using OpenAI setup
            String transcription = openAISetup.stt(path.toFile());
            String response = openAISetup.getResponse(transcription);

            // Store the response in session
            setContentValue(response);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing file: " + e.getMessage());
        }
    }

    // Endpoint to provide text-to-speech response
    @GetMapping("/tts")
    public ResponseEntity<byte[]> textToSpeech() throws IOException {
        // Retrieve the session-specific content value
        String contentValue = getContentValue();

        // Convert the content value to audio
        byte[] audioBytes = openAISetup.tts(contentValue);

        if (audioBytes != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/wav") // Or the appropriate audio content type
                    .body(audioBytes);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
