import http from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";
const root = new URL("./", import.meta.url).pathname;
const types = { ".html":"text/html", ".js":"text/javascript", ".css":"text/css" };
http.createServer(async (req, res) => {
  const path = req.url === "/" ? "demo/index.html" : req.url.slice(1);
  try { const data = await readFile(join(root, path)); res.writeHead(200, {"Content-Type":types[extname(path)]||"text/plain"}); res.end(data); }
  catch { res.writeHead(404); res.end("Not found"); }
}).listen(4173, "127.0.0.1", () => console.log("Prototype: http://localhost:4173"));
