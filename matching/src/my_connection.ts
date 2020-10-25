import { Client } from "@elastic/elasticsearch";

export default new Client({
  node: "http://elasticsearch:9200/",
  requestTimeout: 1000000, //to give more time for indexing
  //   sniffOnStart: true, //discover the rest of the cluster at startup
  // sniffOnConnectionFault: true,
  // sniffInterval: 300,
  suggestCompression: true,
});
