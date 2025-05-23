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
package wvlet.lang.compiler.parser

import wvlet.lang.compiler.{CompilationUnit, Context, Phase, SourceFile}
import wvlet.lang.model.plan.{LogicalPlan, PackageDef}
import wvlet.log.LogSupport

/**
  * Parse *.wv files and create untyped plans
  */
object ParserPhase extends Phase("parser") with LogSupport:

  override def run(unit: CompilationUnit, context: Context): CompilationUnit =
    unit.unresolvedPlan = parse(unit, context)
    unit

  def parseSourceFolder(path: String): Seq[LogicalPlan] = CompilationUnit
    .fromPath(path)
    .map(unit => parse(unit, Context.NoContext))

  def parse(compileUnit: CompilationUnit, ctx: Context): LogicalPlan =
    debug(s"Parsing ${compileUnit.sourceFile}")

    val plan =
      if compileUnit.sourceFile.isSQL then
        val p = SqlParser(unit = compileUnit, isContextUnit = ctx.isContextCompilationUnit)
        p.parse()
      else
        val p = WvletParser(unit = compileUnit, isContextUnit = ctx.isContextCompilationUnit)
        p.parse()

    debug(
      s"[parsed tree for ${compileUnit.sourceFile}:\n${plan.pp}\n${compileUnit
          .sourceFile
          .getContent}"
    )
    plan
