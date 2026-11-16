package com.example.nss.post.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.nss.post.domain.NewsStatus;
import com.example.nss.post.domain.Post;
import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import com.example.nss.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryPostgresIT extends AbstractPostgresSchemaIT {
  @Autowired PostRepository postRepository;
  @Autowired UserRepository userRepository;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void persistsAndRecoversPostAfterClearingPersistenceContext() {
    User author = persistAuthor();
    Post post =
        draft(
            "Boletim semanal",
            "Resumo dos indicadores",
            "boletim-semanal",
            simpleContent(),
            author);

    Post saved = postRepository.saveAndFlush(post);
    entityManager.clear();

    Post reloaded = postRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getId()).isEqualTo(saved.getId());
    assertThat(reloaded.getTitle()).isEqualTo("Boletim semanal");
    assertThat(reloaded.getSubtitle()).isEqualTo("Resumo dos indicadores");
    assertThat(reloaded.getSlug()).isEqualTo("boletim-semanal");
    assertThat(reloaded.getContent()).isEqualTo(simpleContent());
    assertThat(reloaded.getStatus()).isEqualTo(NewsStatus.DRAFT);
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
  }

  @Test
  void persistsAndRecoversCompleteTiptapJsonbDocument() {
    Map<String, Object> document = tiptapDocument();
    Post saved =
        postRepository.saveAndFlush(
            draft("JSONB", null, "jsonb-document", document, persistAuthor()));
    entityManager.clear();

    Map<String, Object> content = postRepository.findById(saved.getId()).orElseThrow().getContent();
    List<Map<String, Object>> paragraphs = maps(content.get("content"));
    List<Map<String, Object>> firstParagraphContent = maps(paragraphs.getFirst().get("content"));
    List<Map<String, Object>> secondParagraphContent = maps(paragraphs.get(1).get("content"));
    List<Map<String, Object>> marks = maps(secondParagraphContent.getFirst().get("marks"));

    assertThat(content).isEqualTo(document);
    assertThat(content.get("type")).isEqualTo("doc");
    assertThat(paragraphs).hasSize(2);
    assertThat(firstParagraphContent.getFirst().get("text"))
        .isEqualTo("Primeiro parágrafo da notícia.");
    assertThat(secondParagraphContent.getFirst().get("text")).isEqualTo("Texto em negrito.");
    assertThat(marks).containsExactly(Map.of("type", "bold"));
  }

  @Test
  void persistsAuthorRelationshipAndDatabaseEnforcesForeignKey() {
    User author = persistAuthor();
    Post saved =
        postRepository.saveAndFlush(draft("Com autor", null, "com-autor", simpleContent(), author));
    entityManager.clear();

    Post reloaded = postRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getAuthor().getId()).isEqualTo(author.getId());
    assertThat(reloaded.getAuthor().getEmail()).isEqualTo(author.getEmail());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT author_id FROM news WHERE id = ?", Long.class, saved.getId()))
        .isEqualTo(author.getId());
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO news (title, slug, content, author_id, status, created_at, updated_at)
                    VALUES ('Autor inexistente', 'autor-inexistente', '{"type":"doc"}'::jsonb, -1,
                            'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findsPostByExistingSlugAndReturnsEmptyForUnknownSlug() {
    Post saved =
        postRepository.saveAndFlush(
            draft(
                "Boletim epidemiológico 2026",
                null,
                "boletim-epidemiologico-2026",
                simpleContent(),
                persistAuthor()));
    entityManager.clear();

    assertThat(postRepository.findBySlug("boletim-epidemiologico-2026"))
        .hasValueSatisfying(post -> assertThat(post.getId()).isEqualTo(saved.getId()));
    assertThat(postRepository.findBySlug("slug-inexistente")).isEmpty();
  }

  @Test
  void identifiesWhetherSlugExists() {
    postRepository.saveAndFlush(
        draft("Existe", null, "slug-existente", simpleContent(), persistAuthor()));

    assertThat(postRepository.existsBySlug("slug-existente")).isTrue();
    assertThat(postRepository.existsBySlug("slug-inexistente")).isFalse();
  }

  @Test
  void databaseRejectsDuplicatedSlug() {
    User author = persistAuthor();
    postRepository.saveAndFlush(draft("Primeira", null, "slug-unico", simpleContent(), author));

    assertThatThrownBy(
            () ->
                postRepository.saveAndFlush(
                    draft("Segunda", null, "slug-unico", simpleContent(), author)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void prePersistSetsCreationAndUpdateTimestampsForDraft() {
    Post saved =
        postRepository.saveAndFlush(
            draft("Datas", null, "datas", simpleContent(), persistAuthor()));

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    assertThat(saved.getPublishedAt()).isNull();
  }

  @Test
  void preUpdateChangesOnlyUpdateTimestampWhenTitleChanges() {
    Post saved =
        postRepository.saveAndFlush(
            draft("Título inicial", null, "pre-update", simpleContent(), persistAuthor()));
    entityManager.clear();
    Instant createdAt = postRepository.findById(saved.getId()).orElseThrow().getCreatedAt();
    jdbcTemplate.update(
        "UPDATE news SET updated_at = ? WHERE id = ?",
        Timestamp.from(Instant.EPOCH),
        saved.getId());
    entityManager.clear();

    Post toUpdate = postRepository.findById(saved.getId()).orElseThrow();
    toUpdate.setTitle("Título atualizado");
    postRepository.saveAndFlush(toUpdate);
    entityManager.clear();

    Post reloaded = postRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getTitle()).isEqualTo("Título atualizado");
    assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    assertThat(reloaded.getUpdatedAt()).isAfter(Instant.EPOCH);
  }

  private User persistAuthor() {
    User user = new User();
    user.setNome("Autora de teste");
    user.setEmail("author-" + UUID.randomUUID() + "@nss.test");
    user.setPassword("Password123!");
    user.setAccountType(AccountType.EDITOR);
    user.setAtivo(true);
    return userRepository.saveAndFlush(user);
  }

  private Post draft(
      String title, String subtitle, String slug, Map<String, Object> content, User author) {
    Post post = new Post();
    post.setTitle(title);
    post.setSubtitle(subtitle);
    post.setSlug(slug);
    post.setContent(content);
    post.setAuthor(author);
    post.setStatus(NewsStatus.DRAFT);
    return post;
  }

  private Map<String, Object> simpleContent() {
    return Map.of("type", "doc", "content", List.of());
  }

  private Map<String, Object> tiptapDocument() {
    return Map.of(
        "type",
        "doc",
        "content",
        List.of(
            Map.of(
                "type",
                "paragraph",
                "content",
                List.of(Map.of("type", "text", "text", "Primeiro parágrafo da notícia."))),
            Map.of(
                "type",
                "paragraph",
                "content",
                List.of(
                    Map.of(
                        "type",
                        "text",
                        "text",
                        "Texto em negrito.",
                        "marks",
                        List.of(Map.of("type", "bold")))))));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> maps(Object value) {
    return (List<Map<String, Object>>) value;
  }
}
