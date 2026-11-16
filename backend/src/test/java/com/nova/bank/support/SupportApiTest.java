package com.nova.bank.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Test authenticated HTTP requests and PostgreSQL persistence. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@org.springframework.transaction.annotation.Transactional
@AutoConfigureMockMvc class SupportApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    String login(String user) throws Exception { var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("token").asText(); }
    @Test void inquiryPersistsAndInvalidRequestsFail() throws Exception {
        String body="{\"topic\":\"Account\",\"subject\":\"Test\",\"message\":\"Example\"}";
        mvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String token=login("miguel");
        String result=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECEIVED")).andExpect(jsonPath("$.estimatedResponse").value("24h")).andReturn().getResponse().getContentAsString();
        String ref=json.readTree(result).get("referenceNumber").asText();
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where reference_number=? and customer_id=? and created_at is not null",Integer.class,ref,UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))).isEqualTo(1);
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\" \",\"subject\":\"x\",\"message\":\"x\"}")).andExpect(status().isBadRequest());
    }
    @Test void chatRejectsUnauthenticatedAndSeparatesPersonas() throws Exception {
        String body="{\"message\":\"balance\"}";
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String rosa=login("rosa"); String response=mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("54,300.00").doesNotContain("142,860.25");
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Speak to Agent\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no live agent was contacted")));
    }
    @Test void inquiryValidationAndScriptPayloadAreContained() throws Exception {
        String token=login("miguel"), payload="<script>alert('xss')</script>";
        UUID customer=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        int before=jdbc.queryForObject("select count(*) from support_inquiries where customer_id=?",Integer.class,customer);
        for (String field:new String[]{"topic","subject","message"}) {
            for (String unsafe:new String[]{payload,"<IMG src=x onerror=alert(1)>","<svg/onload=alert(1)>"}) {
                var body=new java.util.HashMap<String,String>(); body.put("topic","Account"); body.put("subject","Question"); body.put("message","Example"); body.put(field,unsafe);
                mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            }
        }
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("message",payload))))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where customer_id=?",Integer.class,customer)).isEqualTo(before);
        String benign="I paid 5 < 10 & 20 > 15; O'Reilly's account.";
        String response=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("topic","Account","subject","Question","message",benign))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ref=json.readTree(response).get("referenceNumber").asText();
        assertThat(jdbc.queryForObject("select message from support_inquiries where reference_number=?",String.class,ref)).isEqualTo(benign);
        jdbc.update("delete from support_inquiries where reference_number=?",ref);
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("message",benign)))).andExpect(status().isOk());
        for(String invalid:new String[]{"{}","{\"subject\":\"s\",\"message\":\"m\"}","{\"topic\":\"t\",\"message\":\"m\"}","{\"topic\":\"t\",\"subject\":\"s\"}","{\"topic\":null,\"subject\":\"s\",\"message\":\"m\"}",json.writeValueAsString(java.util.Map.of("topic","t".repeat(81),"subject","s","message","m")),json.writeValueAsString(java.util.Map.of("topic","t","subject","s".repeat(161),"message","m")),json.writeValueAsString(java.util.Map.of("topic","t","subject","s","message","m".repeat(4001)))})
            mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        for(String invalid:new String[]{"{}","{\"message\":null}",json.writeValueAsString(java.util.Map.of("message","x".repeat(501)))})
            mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
    }
    @Test void eachInquiryAndChatFieldRejectsInvalidValuesAndAcceptsItsMaximum() throws Exception {
        String token=login("miguel");
        UUID customer=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        int before=jdbc.queryForObject("select count(*) from support_inquiries where customer_id=?",Integer.class,customer);
        for (String field:new String[]{"topic","subject","message"}) {
            int max=field.equals("topic") ? 80 : field.equals("subject") ? 160 : 4000;
            for (Object invalid:java.util.List.of(java.util.Map.of("nested","value"),java.util.List.of("value"),true,false,123,1.5,"", " ","x".repeat(max+1))) {
                var body=new java.util.HashMap<String,Object>(); body.put("topic","Account"); body.put("subject","Question"); body.put("message","Example"); body.put(field,invalid);
                mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            }
            var body=new java.util.HashMap<String,Object>(); body.put("topic","Account"); body.put("subject","Question"); body.put("message","Example"); body.put(field,null);
            mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            body.remove(field);
            mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        for(Object invalid:java.util.List.of(java.util.Map.of("nested","value"),java.util.List.of("value"),true,false,123,1.5,"", " ","x".repeat(501)))
            mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("message",invalid)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"message\":null}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where customer_id=?",Integer.class,customer)).isEqualTo(before);
        for(String field:new String[]{"topic","subject","message"}) {
            int max=field.equals("topic") ? 80 : field.equals("subject") ? 160 : 4000;
            var body=new java.util.HashMap<String,String>(); body.put("topic","Account"); body.put("subject","Question"); body.put("message","Example"); body.put(field,"x".repeat(max));
            String result=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String ref=json.readTree(result).get("referenceNumber").asText();
            assertThat(jdbc.queryForObject("select "+field+" from support_inquiries where reference_number=? and customer_id=?",String.class,ref,customer)).isEqualTo("x".repeat(max));
            jdbc.update("delete from support_inquiries where reference_number=?",ref);
        }
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("message","x".repeat(500))))).andExpect(status().isOk()).andExpect(jsonPath("$.message").isNotEmpty());
    }
}
