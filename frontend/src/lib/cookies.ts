// Reads a single cookie value by name. Returns null when the cookie is absent.
//
// The shared axios instance handles the XSRF token automatically, but the tus
// uploader bypasses axios, so it reads the CSRF cookie itself to echo it back as
// the X-XSRF-TOKEN header on mutating requests.
export function getCookie(name: string): string | null {
  const prefix = `${name}=`
  const match = document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(prefix))

  if (!match) return null
  return decodeURIComponent(match.slice(prefix.length))
}
