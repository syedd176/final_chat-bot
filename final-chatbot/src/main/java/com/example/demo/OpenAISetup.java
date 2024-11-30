package com.example.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Component
public class OpenAISetup {
    //jeetu self trained  open ai chatbot api
    private static final String OPENAI_API_KEY = "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"; // Make sure to replace it with your key
    private static final String BASE_URL = "https://api.openai.com/v1/";
    // Speech-to-text API
    private static final String API_KEY_STT = "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";
    private static final String MODEL_STT = "whisper-1";
    private static final String URL_STT = "https://api.openai.com/v1/audio/transcriptions";
  // text to speech API
    private static final String API_KEY_TTS = "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";
    private static final String API_URL_TTS = "https://api.openai.com/v1/audio/speech";


    public final OkHttpClient client = new OkHttpClient();
    public String contentValue = "";

    public String threadId="";
    String STT="";







    // Thread-safe way to manage thread IDs using RequestContextHolder
    public String getThreadId() {
        String threadId = (String) RequestContextHolder.getRequestAttributes()
                .getAttribute("threadId", RequestAttributes.SCOPE_SESSION);
        if (threadId == null || threadId.isEmpty()) {
            threadId = createThread();
            RequestContextHolder.getRequestAttributes()
                    .setAttribute("threadId", threadId, RequestAttributes.SCOPE_SESSION);
        }
        return threadId;
    }



    public String stt(File tempfile){

        String promptText = "The transcription is related to UET Taxila. Important terms include: Departments like Electrical, Mechanical, and Software Engineering; acronyms like UET, PEC (Pakistan Engineering Council), CGPA, and semester names; events like convocation, admission office, and student societies.";

        RequestBody fileBody = RequestBody.create(tempfile, MediaType.parse("audio/ogg"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", MODEL_STT)
                .addFormDataPart("translate", "true")
                .addFormDataPart("prompt", promptText)
                .addFormDataPart("file", tempfile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(URL_STT)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + API_KEY_STT)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                 String responseText = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseText);
                 getResponse( jsonResponse.getString("text"));
                STT = jsonResponse.getString("text");


            } else {
                System.err.println("Request failed: " + response.message());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return STT  ;
    }


     public  byte[] tts(String contentValue) throws IOException {


            String result = contentValue.replaceAll("[\\t\\n\\*]", "");
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();


            String jsonBody = "{"
                    + "\"model\": \"tts-1\","
                    + "\"voice\": \"" + "fable" + "\","
                    + "\"input\": \"" + result + "\""
                    + "}";

            Request request = new Request.Builder()
                    .url(API_URL_TTS)
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + API_KEY_TTS)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return response.body().bytes();
                } else {
                    System.out.println("TTS request failed: " + response.message());
                    return null;
                }
            }





    }


    // Method to handle the assistant's response
    public String getResponse(String userMessage) throws IOException {
        String threadId = getThreadId();

        if(threadId.isEmpty()){

            contentValue="Error Creating Thread";
        }

        else {

            addMessage(threadId, "user", userMessage);
//            createAndPollRun(threadId, "asst_WW0Lll1ZMLBod2DUrolsUetL");
//            runPythonScript(threadId, out);
        }

        // Fetch the response message from OpenAI
        return contentValue;
    }

    public String createThread() {
        JsonObject threadRequestBody = new JsonObject();
        Request request = new Request.Builder()
                .url(BASE_URL + "threads")
                .post(RequestBody.create(threadRequestBody.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("OpenAI-Beta", "assistants=v2")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JsonObject responseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
                return responseObject.get("id").getAsString();
            } else {
                System.out.println("Failed to create thread: " + response.code() + " " + response.message());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addMessage(String threadId, String role, String content) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", role);
        requestBody.addProperty("content", content);


        Request request = new Request.Builder()
                .url(BASE_URL + "threads/" + threadId + "/messages")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("OpenAI-Beta", "assistants=v2")
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void streamAssistantResponse(String threadId, String instructions, BufferedWriter writer) throws Exception {

        String url = "https://api.openai.com/v1/threads/" + threadId + "/runs";
        ;



        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("instructions", instructions);
        requestBody.addProperty("stream", true);
        requestBody.addProperty("assistant_id", "asst_WW0Lll1ZMLBod2DUrolsUetL");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + "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")
                .header("OpenAI-Beta", "assistants=v2")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            handleEvent(line, writer);
                        }
                    } catch (IOException e) {
                        System.err.println("Error reading response: " + e.getMessage());
                    }
                });

        future.join();
    }



//    private Writer writer;  // Writer to send content to frontend (WebSocket, HTTP, etc.)
//
//    public void EventHandler(Writer writer) {
//        this.writer = writer;  // Initialize writer (could be a WebSocket or HTTP stream)
//    }

    void handleEvent(String line, BufferedWriter writer) {

        try {
            if (line.startsWith("data:")) {
                String json = line.substring(5).trim();
                if (!json.isEmpty()) {
                    // First check if the response is a JSON array
                    if (json.startsWith("[") && json.endsWith("]")) {
                        // Handle the ["DONE"] case (stream finished)
                        JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
                        if (jsonArray.size() == 1 && jsonArray.get(0).getAsString().equals("DONE")) {
                            System.out.println("Stream finished.");
                            return; // Exit or handle the end of the stream
                        }
                    } else {
                        // If not an array, proceed with parsing as JSON object
                        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

                        // Check if the "delta" field exists
                        if (jsonObject.has("delta")) {
                            JsonObject delta = jsonObject.getAsJsonObject("delta");

                            // Check if "content" is an array and contains items
                            if (delta.has("content") && delta.get("content").isJsonArray()) {
                                var contentArray = delta.getAsJsonArray("content");

                                // Process each element in the "content" array
                                for (var item : contentArray) {
                                    if (item.isJsonObject()) {
                                        JsonObject contentItem = item.getAsJsonObject();

                                        // Check if the "text" field exists in the content item
                                        if (contentItem.has("text")) {
                                            JsonObject textObject = contentItem.getAsJsonObject("text");

                                            // Check if "value" exists and is not null
                                            if (textObject.has("value") && textObject.get("value") != null) {
                                                String content = textObject.get("value").getAsString();
                                                contentValue=textObject.get("value").getAsString();
                                                sendToFrontend(content, writer);


                                            } else {
                                                System.out.println("Text value is missing or null.");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing event: " + e.getMessage());
        }

    }

    // This method simulates sending the content to the frontend, it can be replaced with actual frontend communication logic (e.g., WebSocket, HTTP response streaming)
    public void sendToFrontend(String content, BufferedWriter writer) throws IOException {
        // Replace this with actual logic to send content to the frontend (e.g., via WebSocket or HTTP response)
        writer.write(content);
        // Send progressive content to frontend

        writer.flush();  // Ensure immediate delivery
        System.out.print(content);  // Optionally log to console
    }













//exluded code
//    public void createAndPollRun(String threadId, String assistantId) {
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("assistant_id", assistantId);
//
//        Request request = new Request.Builder()
//                .url(BASE_URL + "threads/" + threadId + "/runs")
//                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
//                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
//                .addHeader("OpenAI-Beta", "assistants=v2")
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful()) {
//                JsonObject responseObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
//                String runId = responseObject.get("id").getAsString();
//
//                pollForRunCompletion(threadId, runId);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }













/// //////////I have exluded this code
//
//    public void pollForRunCompletion(String threadId, String runId) {
//        String runStatus;
//        int count = 2;
//        do {
//            runStatus = getRunStatus(threadId, runId);
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        } while (runStatus != null && !runStatus.equals("completed") && !runStatus.equals("failed"));
//
//        if ("completed".equals(runStatus)) {
//            fetchMessages(threadId);
//        }
//    }
//
//    public String getRunStatus(String threadId, String runId) {
//        Request request = new Request.Builder()
//                .url(BASE_URL + "threads/" + threadId + "/runs/" + runId)
//                .get()
//                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
//                .addHeader("OpenAI-Beta", "assistants=v2")
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful()) {
//                JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
//                return jsonObject.get("status").getAsString();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    public void  fetchMessages(String threadId) {
//        Request request = new Request.Builder()
//                .url(BASE_URL + "threads/" + threadId + "/messages")
//                .get()
//                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
//                .addHeader("OpenAI-Beta", "assistants=v2")
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful()) {
//                String responseBody = response.body().string();
//                JSONObject jsonResponse = new JSONObject(responseBody);
//
//                contentValue = jsonResponse.getJSONArray("data")
//                        .getJSONObject(0)
//                        .getJSONArray("content")
//                        .getJSONObject(0)
//                        .getJSONObject("text")
//                        .getString("value");
//
//
//
//
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }



