package com.example.nss.post.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostSchemaPostgresIT extends AbstractPostgresSchemaIT {
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void appliesNewsMigrationWithJsonbAndAuthorForeignKeyBeforeHibernateValidation() {
    Map<String, Object> contentColumn =
        jdbcTemplate.queryForMap(
            """
            SELECT data_type, udt_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'news'
              AND column_name = 'content'
            """);

    Integer authorForeignKeys =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.table_constraints constraints
            JOIN information_schema.key_column_usage key_columns
              ON constraints.constraint_name = key_columns.constraint_name
             AND constraints.table_schema = key_columns.table_schema
            JOIN information_schema.constraint_column_usage referenced_columns
              ON constraints.constraint_name = referenced_columns.constraint_name
             AND constraints.table_schema = referenced_columns.table_schema
            WHERE constraints.constraint_type = 'FOREIGN KEY'
              AND constraints.table_schema = current_schema()
              AND constraints.table_name = 'news'
              AND key_columns.column_name = 'author_id'
              AND referenced_columns.table_name = 'users'
              AND referenced_columns.column_name = 'id'
            """,
            Integer.class);

    assertThat(contentColumn)
        .containsEntry("data_type", "jsonb")
        .containsEntry("udt_name", "jsonb");
    assertThat(authorForeignKeys).isEqualTo(1);
  }
}
