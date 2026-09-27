import { useEffect, useState } from "react";
import { getAccount, subscribeAuth } from "./api.js";

/** Текущая учётная запись (или null для анонимной игры); обновляется при входе/выходе. */
export function useAccount() {
  const [account, setAccount] = useState(getAccount);
  useEffect(() => subscribeAuth(setAccount), []);
  return account;
}

export function isVerified(account) {
  return !!(account && account.verified);
}

export function initialsOf(name) {
  const parts = String(name || "??").trim().split(/\s+/).filter(Boolean);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : String(name || "??").slice(0, 2)).toUpperCase();
}
