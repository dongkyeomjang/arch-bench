package com.example.lending;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LendingIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void loaning_a_book_already_on_loan_returns_409() throws Exception {
        String registered = mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Architecture\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode book = objectMapper.readTree(registered);
        String member = mockMvc.perform(post("/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kim\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberId = objectMapper.readTree(member).get("memberId").asText();
        String loanRequest = objectMapper.writeValueAsString(new LoanBody(book.get("id").asText(), memberId));

        mockMvc.perform(post("/loans").contentType(MediaType.APPLICATION_JSON).content(loanRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(book.get("id").asText()));

        mockMvc.perform(post("/loans").contentType(MediaType.APPLICATION_JSON).content(loanRequest))
                .andExpect(status().isConflict());
    }

    record LoanBody(String bookId, String memberId) {
    }
}
