-- Bloques cifrados de la sincronización (docs/SINCRONIZACION.md): el servidor no tiene la clave
create table bovedas (
  id text primary key,        -- SHA-256 del token, en hexadecimal
  version integer not null,
  datos blob not null,        -- formato, nonce, cifrado y etiqueta; nunca se descifra aquí
  actualizado text not null   -- ISO 8601
);
