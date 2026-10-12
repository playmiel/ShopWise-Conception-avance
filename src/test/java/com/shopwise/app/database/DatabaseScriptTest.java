package com.shopwise.app.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Garantit que le livrable bdd/script.sql reste fidele au schema reellement utilise :
 * il doit contenir chaque migration Flyway et s'executer sur une base vide.
 */
@DisplayName("Integration - livrable bdd/script.sql")
class DatabaseScriptTest {

	private static final Path SCRIPT = Path.of("bdd", "script.sql");
	private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

	private static String normalize(String sql) {
		return sql.replace("\r\n", "\n").trim();
	}

	private static int version(Path migration) {
		String name = migration.getFileName().toString();
		return Integer.parseInt(name.substring(1, name.indexOf("__")));
	}

	@Test
	@DisplayName("contient toutes les migrations Flyway, dans l'ordre")
	void containsAllMigrations() throws IOException {
		String script = normalize(Files.readString(SCRIPT, StandardCharsets.UTF_8));
		List<Path> migrations;
		try (Stream<Path> files = Files.list(MIGRATIONS)) {
			migrations = files.filter(p -> p.getFileName().toString().matches("V\\d+__.+\\.sql"))
					.sorted(Comparator.comparingInt(DatabaseScriptTest::version))
					.toList();
		}

		assertThat(migrations).isNotEmpty();
		int previousIndex = -1;
		for (Path migration : migrations) {
			String content = normalize(Files.readString(migration, StandardCharsets.UTF_8));
			int index = script.indexOf(content);
			assertThat(index).as("bdd/script.sql doit contenir %s (regenerer avec bdd/generate-script.ps1)",
					migration.getFileName()).isGreaterThan(previousIndex);
			previousIndex = index;
		}
	}

	@Test
	@DisplayName("s'execute sur une base vide et cree tables et donnees initiales")
	void runsOnEmptyDatabase() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:script_check_" + System.nanoTime(), "sa", "")) {
			ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(SCRIPT), StandardCharsets.UTF_8));

			try (Statement st = connection.createStatement()) {
				assertThat(count(st, "SELECT COUNT(*) FROM products")).isEqualTo(4);
				assertThat(count(st, "SELECT COUNT(*) FROM sales")).isZero();
				assertThat(count(st, "SELECT COUNT(*) FROM sale_lines")).isZero();
			}
		}
	}

	private static int count(Statement st, String sql) throws Exception {
		try (ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getInt(1);
		}
	}
}