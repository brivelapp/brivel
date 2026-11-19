-- Sin el tope de 1 MB (decisión de Gabriel, 2026-11-19): D1 admite como mucho 2 MB por fila, así que la copia cifrada
-- se guarda en trozos de 1 MB. `escritura` marca la subida que escribió los trozos: solo esa subida puede cambiarlos,
-- aunque otra llegue a la vez (docs/SINCRONIZACION.md)
create table trozos (
  id text not null,           -- la cuenta (bovedas.id)
  n integer not null,         -- orden del trozo, desde 0
  datos blob not null,
  primary key (id, n)
);
alter table bovedas add column escritura text;
