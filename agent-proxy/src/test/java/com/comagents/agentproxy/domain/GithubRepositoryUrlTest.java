package com.comagents.agentproxy.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GithubRepositoryUrlTest {

    @Test
    void readsOwnerAndRepository() {
        GithubRepositoryUrl url = GithubRepositoryUrl.parse("https://github.com/acme/kn-challenge.git");

        assertEquals(new GithubRepositoryUrl("acme", "kn-challenge"), url);
        assertEquals("acme/kn-challenge", url.fullName());
        assertEquals("kn-challenge", GithubRepositoryUrl.parse("https://github.com/acme/kn-challenge/").repository());
        assertEquals("kn-challenge", GithubRepositoryUrl.parse("https://github.com/acme/kn-challenge").repository());
    }

    @Test
    void rejectsOtherUrls() {
        assertEquals("Not a GitHub repository URL: https://gitlab.com/acme/kn-challenge.git", assertThrows(
                IllegalArgumentException.class, () -> GithubRepositoryUrl.parse("https://gitlab.com/acme/kn-challenge.git")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> GithubRepositoryUrl.parse("https://github.com/acme/kn-challenge/tree/main"));
        assertThrows(IllegalArgumentException.class, () -> GithubRepositoryUrl.parse("https://github.com/acme/.."));
        assertThrows(IllegalArgumentException.class, () -> GithubRepositoryUrl.parse(" "));
    }
}
