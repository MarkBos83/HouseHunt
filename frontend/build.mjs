// Bundles the React app and its CSS into one self-contained template.
// The Kotlin publisher later replaces <!--ENCRYPTED_DATA--> with the encrypted listings.
import { build } from "esbuild";
import { mkdir, readFile, writeFile } from "node:fs/promises";

const outputDirectory = "dist";

async function bundleApplication() {
  const result = await build({
    entryPoints: ["src/index.tsx"],
    bundle: true,
    minify: true,
    write: false,
    outdir: outputDirectory,
    format: "iife",
    target: "es2020",
    jsx: "automatic",
    loader: { ".css": "css" },
    define: { "process.env.NODE_ENV": '"production"' },
    legalComments: "none",
  });
  const script = findOutput(result, ".js");
  const styles = findOutput(result, ".css");
  return { script: escapeClosingTags(script, "script"), styles: escapeClosingTags(styles, "style") };
}

function findOutput(result, extension) {
  const file = result.outputFiles.find((candidate) => candidate.path.endsWith(extension));
  if (!file) throw new Error(`esbuild produced no ${extension} output`);
  return file.text;
}

// A literal "</script" inside inlined code would end the tag early.
function escapeClosingTags(text, tagName) {
  return text.split(`</${tagName}`).join(`<\\/${tagName}`);
}

// split/join instead of replace: bundled code contains "$" sequences that replace() would interpret.
function fillTemplate(template, { script, styles }) {
  return template.split("<!--STYLES-->").join(styles).split("<!--SCRIPT-->").join(script);
}

async function main() {
  const template = await readFile("src/template.html", "utf8");
  const html = fillTemplate(template, await bundleApplication());
  if (!html.includes("<!--ENCRYPTED_DATA-->")) throw new Error("Template lost its ENCRYPTED_DATA marker");
  await mkdir(outputDirectory, { recursive: true });
  await writeFile(`${outputDirectory}/template.html`, html);
  console.log(`Wrote ${outputDirectory}/template.html (${html.length} bytes)`);
}

main();
