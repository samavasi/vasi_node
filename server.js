'use strict';

const express = require('express');

// Constants
const PORT = 8080;
const HOST = '0.0.0.0';
const os = require("os");

// App
const app = express();
app.get('/', (req, res) => {
  let currDate = new Date().toUTCString();
  res.setHeader('Content-Type', 'text/html');
  res.write(`<h2>Running on OS -> platform: ${os.platform()}, release: ${os.release()}</h2>` );
  //end the response process
  res.end();
});

app.listen(PORT, HOST);
console.log(`Running on http://${HOST}:${PORT}`);
