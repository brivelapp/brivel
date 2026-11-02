-- Bloques cifrados de la sincronización (docs/SINCRONIZACION.md): el servidor no tiene la clave
create table bovedas (
  id text primary key,        -- SHA-256 del token, en hexadecimal
  version integer not null,
  datos blob not null,        -- formato, nonce, cifrado y etiqueta; nunca se descifra aquí
  usado text not null         -- último uso (subida o bajada), ISO 8601: a los 12 meses sin uso se borra
);
