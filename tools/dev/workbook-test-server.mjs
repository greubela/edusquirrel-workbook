import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';

const root=path.resolve(import.meta.dirname,'../..');
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.wasm':'application/wasm'};

// importScripts is synchronous. Serving its assets directly avoids a DevTools
// interception pause while the worker thread waits for that same network request.
export async function startWorkbookTestServer() {
  let origin;
  const server=http.createServer(async(request,response)=>{
    try {
      const url=new URL(request.url,origin);
      const allowed=['/homepage/','/artifacts/newest/','/resources/','/deployment/client/','/pyodide/'];
      if(!allowed.some(prefix=>url.pathname.startsWith(prefix))) {response.writeHead(404).end();return;}
      let file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
      if(url.pathname.startsWith('/pyodide/')) {
        const name=url.pathname.slice('/pyodide/'.length);
        if(name.includes('/')||name.includes('..')) {response.writeHead(404).end();return;}
        file=path.join(root,'node_modules/pyodide',name);
      }
      if(!file.startsWith(root+path.sep)) {response.writeHead(404).end();return;}
      if(url.pathname.endsWith('/CodeMirrorLoader.js')) file=process.env.CODEMIRROR_TEST_BUNDLE;
      let body=await fs.readFile(file);
      if(url.pathname==='/artifacts/newest/worker.js') body=Buffer.from(body.toString().replaceAll(
        'https://cdn.jsdelivr.net/pyodide/v0.29.3/full/',origin+'/pyodide/'));
      response.writeHead(200,{'content-type':mime[path.extname(file)]||'application/octet-stream'}).end(body);
    } catch {response.writeHead(404).end();}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  origin='http://127.0.0.1:'+server.address().port;
  return {origin,close:()=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);})};
}
