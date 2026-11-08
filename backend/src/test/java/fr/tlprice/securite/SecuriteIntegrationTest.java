package fr.tlprice.securite;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouille les correctifs de securite : une regression sur l un de ces
 * points expose directement l app publiee sur Internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:securite;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "tlprice.cle-chiffrement=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "tlprice.inscription.ouverte=false",
    "tlprice.sauvegarde.repertoire=target/sauvegardes-test"
})
class SecuriteIntegrationTest {

    private static final String EMAIL = "premier@exemple.fr";
    private static final String MDP = "motdepasse-solide";

    @Autowired
    MockMvc mvc;

    @Test
    @Order(1)
    void premierCompteToujoursPossibleSurBaseVide() throws Exception {
        mvc.perform(get("/api/auth/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inscriptionOuverte").value(true));

        mvc.perform(post("/api/auth/inscription").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"motDePasse\":\"" + MDP + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void inscriptionFermeeEnsuite() throws Exception {
        mvc.perform(get("/api/auth/config"))
            .andExpect(jsonPath("$.inscriptionOuverte").value(false));

        mvc.perform(post("/api/auth/inscription").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"intrus@exemple.fr\",\"motDePasse\":\"12345678\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(3)
    void leHashDuMotDePasseNeSortJamais() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", EMAIL).param("password", MDP))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        String corps = mvc.perform(get("/api/solde").session(session))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(corps).contains("solde");
        assertThat(corps).doesNotContain("motDePasseHash").doesNotContain("$2a$")
            .doesNotContain("utilisateur");
    }

    @Test
    @Order(4)
    void entetesDeSecurite() throws Exception {
        mvc.perform(get("/api/auth/config"))
            .andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @Order(5)
    void bruteforceBloqueApresCinqEchecs() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf())
                    .param("username", EMAIL).param("password", "mauvais" + i))
                .andExpect(status().isUnauthorized());
        }
        // Meme le BON mot de passe est refuse pendant le blocage.
        mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", EMAIL).param("password", MDP))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }
}
