package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.Config.OpenAiProperties;
import com.codeSense_Ai.Dto.OpenAiRawResponse;
import com.codeSense_Ai.Dto.OpenAiResponse;
import com.codeSense_Ai.Exceptions.OpenAIException;
import com.codeSense_Ai.Exceptions.OpenAIRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiClient {
     private final RestClient restClient;
     private final OpenAiProperties openAiProperties;

     public OpenAiResponse call(String SystemPrompt , String userPrompt , String model){
         Map<String , Object>  requestBody  =  Map.of(
                 "model"  , model,
                 "max_tokens", openAiProperties.getMaxTokens(),

                 "response_format" , Map.of("type" , "json_object"),

                 "messages", List.of(
                         Map.of("role", "system", "content", SystemPrompt),
                         Map.of("role", "user",   "content", userPrompt)
                 ),

                 "temperature" , 0
         );

         try{
             Long startMs = System.currentTimeMillis();

             OpenAiRawResponse raw = restClient.post()
                     .uri(openAiProperties.getBaseUrl() + "/chat/completions")
                     .header(HttpHeaders.AUTHORIZATION, openAiProperties.getAuthorizationHeader())
                     .body(requestBody)
                     .retrieve()
                     .body(OpenAiRawResponse.class);

             long elapsedMs = System.currentTimeMillis() - startMs;

             if (raw == null || raw.choices() == null || raw.choices().isEmpty()) {
                 throw new OpenAIException("Empty response from OpenAI");
             }
             String content = raw.choices().get(0).message().content();
             int promptTokens = raw.usage().promptTokens();
             int completionTokens = raw.usage().completionTokens();

             log.info("OpenAI responded: model={} tokens={}/{} elapsed={}ms",
                     model, promptTokens, completionTokens, elapsedMs);

             return new OpenAiResponse(content, promptTokens, completionTokens, model);
         }catch (HttpClientErrorException.TooManyRequests e){

             log.warn("OpenAI rate limited: {}", e.getResponseBodyAsString());
             throw new OpenAIRateLimitException("OpenAI rate limit hit");

         }catch (HttpClientErrorException.BadRequest e){
             log.error("OpenAI bad request: {}", e.getResponseBodyAsString());
             throw new OpenAIException("Bad request to OpenAI: " + e.getMessage());
         }catch (Exception e) {
             log.error("OpenAI call failed: {}", e.getMessage());
             throw new OpenAIException("OpenAI call failed: " + e.getMessage());
         }
     }
}
