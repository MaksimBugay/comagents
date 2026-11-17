package com.comagents.commons.domain;

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

    @Test
    void acceptsOnlyCharactersGithubAllows() {
        assertEquals(new GithubRepositoryUrl("my-org", "repo_1.v2"), GithubRepositoryUrl.parse("https://github.com/my-org/repo_1.v2.git"));
        for (String url : new String[]{
                "https://github.com/acme/repo?x=1",
                "https://github.com/acme/repo#readme",
                "https://github.com/acme/re%2Fpo",
                "https://github.com/acme/my repo",
                "https://github.com/../repo",
                "https://github.com/ac.me/repo"}) {
            assertThrows(IllegalArgumentException.class, () -> GithubRepositoryUrl.parse(url), url);
        }
    }
}
