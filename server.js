'use strict';

const express = require('express');
const MongoClient = require('mongodb').MongoClient;
var url = process.env.DB_CONNECT_URL

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
  res.write(`<h2>Xendit - Trial - Vasileios Samaras - Tue, 22 Mar 2021 02:00:00 GMT - ${currDate}</h2>` );
  var memUsage = JSON.stringify(process.memoryUsage());
  res.write(`<h2>Memory usage: ${memUsage}</h2>` );
  var cpuUsage = JSON.stringify(process.cpuUsage());
  res.write(`<h2>Cpu usage: ${cpuUsage}</h2>`);

  MongoClient.connect(url, function(err, db) {
    if (err) throw err;
    var dbo = db.db("xendit");
    var timestamp = new Date().getTime();
    var myobj = { logentry: timestamp };
    dbo.collection("logins").insertOne(myobj, function(err, res) {
      if (err) throw err;
      console.log("1 document inserted");
      db.close();
    });
  });
  //end the response process
  res.end();
});

app.get('/health', (req, res) => {
  res.write(`OK`);
  //end the response process
  res.end();
});

app.listen(PORT, HOST);
console.log(`Running on http://${HOST}:${PORT}`);
