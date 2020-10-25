export type Options = {
  batchSize: number;
  docType: string;
  indexName: string;
  dump?: string;
  concurrency: number;
  action: string;
  start: Date;
};

export type BiblObj = {
  _id: string;
  title: string;
  DOI: string;
  first_author: string;
  author?: string;
  first_page: string;
  journal: string;
  abbreviated_journal: string;
  volume: string;
  issue?: string;
  year: number;
  bibliographic: string;
  type: string;
};

export type RawCrossrefData = {
  title: string;
  DOI: string;
  author?: { sequence: string; family: string }[];
  page?: string;
  type: string;
  "container-title": string;
  "short-container-title": string;
  volume: string;
  issue?: string;
  issued?: {
    "date-parts": number[][];
  };
  "published-online"?: {
    "date-parts": number[][];
  };
  "published-print"?: {
    "date-parts": number[][];
  };
  created?: {
    "date-parts": number[][];
  };
};

export type ElasticIndexHeader = {
  index: {
    _index: string;
    _type: string;
  };
};
