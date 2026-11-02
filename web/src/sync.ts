// Llaves de la sincronización en IndexedDB (docs/SINCRONIZACION.md, auditoría 1). La clave es una CryptoKey no
// exportable: IndexedDB la guarda tal cual y ningún script puede leer sus bytes. El código no se guarda.
import { WebSyncKeys } from "brivel-shared"

/** Versión de la política de privacidad que se acepta al activar (CUMPLIMIENTO #6: fecha y versión). */
export const POLICY_VERSION = "2026-11-02.2"

interface Stored { key: CryptoKey; bearer: string; account: string; consentAt: string; policy: string }

function request<T>(mode: IDBTransactionMode, f: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open("brivel", 1)
    open.onupgradeneeded = () => open.result.createObjectStore("sync")
    open.onerror = () => reject(open.error)
    open.onsuccess = () => {
      const req = f(open.result.transaction("sync", mode).objectStore("sync"))
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error)
    }
  })
}

export async function loadKeys(): Promise<WebSyncKeys | null> {
  const s = await request<Stored | undefined>("readonly", (store) => store.get("llaves")).catch(() => undefined)
  return s ? new WebSyncKeys(s.key, s.bearer, s.account) : null
}

// Un objeto plano: la clase de Kotlin no sobrevive a la copia de IndexedDB
export const saveKeys = (k: WebSyncKeys) => request("readwrite", (store) => store.put(
  { key: k.key, bearer: k.bearer, account: k.account, consentAt: new Date().toISOString(), policy: POLICY_VERSION } satisfies Stored,
  "llaves",
))

export const clearKeys = () => request("readwrite", (store) => store.delete("llaves")).catch(() => undefined)
