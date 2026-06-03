/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Gateway origin for absolute login/logout URLs. Default: http://localhost:8080 */
  readonly VITE_GATEWAY_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
