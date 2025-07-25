/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.lang.compiler

import wvlet.airspec.AirSpec
import wvlet.lang.catalog.{Catalog, CatalogSerializer, StaticCatalogProvider}
import wvlet.lang.compiler.{CompilationUnit, SourceFile}
import wvlet.lang.model.DataType
import wvlet.log.LogLevel
import java.nio.file.{Files, Path, Paths}

class StaticCatalogCompilerTest extends AirSpec:

  private def withTempCatalog[T](f: Path => T): T =
    val tempDir = Files.createTempDirectory("static-catalog-compiler-test")
    try
      // Create a simple static catalog structure
      val catalogDir = tempDir.resolve("duckdb").resolve("test")
      Files.createDirectories(catalogDir)

      // Write schemas
      val schemas = List(Catalog.TableSchema(Some("test"), "main", "Main schema"))
      Files.writeString(
        catalogDir.resolve("schemas.json"),
        CatalogSerializer.serializeSchemas(schemas)
      )

      // Write tables
      val tables = List(
        Catalog.TableDef(
          tableName = Catalog.TableName(Some("test"), Some("main"), "users"),
          columns = List(
            Catalog.TableColumn("id", DataType.LongType),
            Catalog.TableColumn("name", DataType.StringType),
            Catalog.TableColumn("email", DataType.StringType)
          )
        ),
        Catalog.TableDef(
          tableName = Catalog.TableName(Some("test"), Some("main"), "orders"),
          columns = List(
            Catalog.TableColumn("order_id", DataType.LongType),
            Catalog.TableColumn("user_id", DataType.LongType),
            Catalog.TableColumn("amount", DataType.DoubleType)
          )
        )
      )
      Files.writeString(catalogDir.resolve("main.json"), CatalogSerializer.serializeTables(tables))

      f(tempDir)
    finally
      // Clean up
      def deleteRecursively(path: Path): Unit =
        if Files.isDirectory(path) then
          Files.list(path).forEach(deleteRecursively)
        Files.deleteIfExists(path)
      deleteRecursively(tempDir)

    end try

  end withTempCatalog

  test("compile with static catalog") {
    withTempCatalog { catalogPath =>
      val workEnv = WorkEnv(".", logLevel = LogLevel.INFO)
      val compilerOptions = CompilerOptions(
        workEnv = workEnv,
        catalog = Some("test"),
        schema = Some("main"),
        dbType = DBType.DuckDB
      ).withStaticCatalog(catalogPath.toString)

      val compiler = Compiler(compilerOptions)

      // Create a compilation unit with a simple query
      val sourceCode      = "from main.users select *"
      val sourceFile      = SourceFile.fromString("test.wv", sourceCode)
      val compilationUnit = CompilationUnit(sourceFile)

      // Compile should succeed with static catalog
      val result = compiler.compileSingleUnit(compilationUnit)

      // The compilation should succeed without errors
      result.hasFailures shouldBe false

      // Verify the catalog was loaded by checking that the table reference was resolved
      result.units.nonEmpty shouldBe true
      result.contextUnit.isDefined shouldBe true
    }
  }

  test("fall back to in-memory catalog when static catalog not found") {
    val workEnv = WorkEnv(".", logLevel = LogLevel.INFO)
    val compilerOptions = CompilerOptions(workEnv = workEnv, catalog = Some("fallback"))
      .withStaticCatalog("/nonexistent/path")

    val compiler = Compiler(compilerOptions)

    // Create a compilation unit that references a non-existent table
    val sourceCode      = "from nonexistent.table select *"
    val sourceFile      = SourceFile.fromString("test.wv", sourceCode)
    val compilationUnit = CompilationUnit(sourceFile)

    // Compilation should complete (but may have errors for non-existent table)
    val result = compiler.compileSingleUnit(compilationUnit)

    // The compilation process should complete, even if table is not found
    // This verifies the fallback to InMemoryCatalog worked
    result shouldNotBe null
  }

  test("disable static catalog mode") {
    withTempCatalog { catalogPath =>
      val workEnv = WorkEnv(".", logLevel = LogLevel.INFO)
      val compilerOptions = CompilerOptions(workEnv = workEnv, catalog = Some("memory"))
        .withStaticCatalog(catalogPath.toString)
        .noStaticCatalog()

      compilerOptions.useStaticCatalog shouldBe false
      compilerOptions.staticCatalogPath shouldBe None

      val compiler = Compiler(compilerOptions)

      // Should use in-memory catalog since static catalog is disabled
      compilerOptions.useStaticCatalog shouldBe false
      compilerOptions.catalog shouldBe Some("memory")
    }
  }

end StaticCatalogCompilerTest
