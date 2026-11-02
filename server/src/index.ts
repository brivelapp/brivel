export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url)
    if (pathname === "/api/estado") {
      const row = await env.DB.prepare("select 1 as ok").first<{ ok: number }>()
      return Response.json({ ok: row?.ok === 1 })
    }
    return Response.json({ error: "No encontrado" }, { status: 404 })
  },
} satisfies ExportedHandler<Env>
