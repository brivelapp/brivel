-- Entrar con Google o Apple (decisión de Gabriel, 2026-11-19; docs/SINCRONIZACION.md, «Cuentas»): el servidor guarda el
-- secreto de sincronización de cada cuenta, cifrado con una clave del Worker (CUENTAS_CLAVE), y se lo da al dispositivo
-- que entra. Ni correo ni nombre: solo un identificador que no se puede volver atrás.
create table identidades (
  id text primary key,        -- HMAC-SHA256(CUENTAS_PIMIENTA, "proveedor:sub") en hexadecimal
  proveedor text not null,    -- google | apple
  secreto blob not null,      -- nonce + AES-256-GCM del secreto (16 bytes); datos asociados: id
  boveda text not null,       -- bovedas.id de ese secreto: se borran juntas
  revocar blob,               -- Apple: token de refresco cifrado, para revocarlo al borrar (lo exige Apple)
  creada text not null
);
create index identidades_boveda on identidades (boveda);

-- Billete de un solo uso entre la vuelta del proveedor y el dispositivo (2 minutos)
create table billetes (
  id text primary key,        -- SHA-256 del billete
  identidad text not null,
  proveedor text not null,
  revocar blob,
  caduca text not null
);
