import { BiblObj } from "./index";

export const massage = (data: string) => JSON.parse(data);

export const filterType = (doc: BiblObj): boolean => doc.type === "component";

export const round = (n: number) => Math.round(n * 100) / 100;

export const sleep = async (ms: number) => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};
