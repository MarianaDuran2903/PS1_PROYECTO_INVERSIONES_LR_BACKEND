# Backups de la base de datos

`backup-db.sh` saca un `pg_dump` de PostgreSQL cada 3 horas, lo verifica y lo copia por SSH a otro equipo.
Corre en el servidor Linux donde está levantado el `docker compose`.

## Cómo evita saturar el servidor

| Recurso | Medida |
|---|---|
| CPU | `pg_dump` y la compresión corren con `nice -n 19` (prioridad mínima). |
| Disco | `ionice -c 3`: solo lee del disco cuando nadie más lo usa. |
| Red | `rsync --bwlimit` (por defecto 5 MB/s). |
| Ejecuciones encimadas | `flock`: si un backup sigue corriendo, el siguiente se omite. |
| Espacio | Se guardan 2 días en el servidor y 14 en el equipo remoto. |
| Bloqueos | `pg_dump` no bloquea lecturas ni escrituras; `--lock-wait-timeout` evita esperar a una migración. |

## Instalación en el servidor

1. Configurar:
   ```bash
   cp scripts/backup/backup.env.example scripts/backup/backup.env
   chmod +x scripts/backup/*.sh
   ```
   Editar `backup.env` con el equipo remoto (`REMOTE_HOST`, `REMOTE_USER`, `REMOTE_DIR`).

2. Crear una llave SSH solo para backups y autorizarla en el equipo remoto:
   ```bash
   ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519_backup
   ssh-copy-id -i ~/.ssh/id_ed25519_backup.pub backup@IP_DEL_REMOTO
   ```
   El equipo remoto necesita `rsync` (si no lo tiene, el script usa `scp`).

3. Probar a mano:
   ```bash
   scripts/backup/backup-db.sh
   ```

4. Programar cada 3 horas (`crontab -e` con el usuario que puede usar Docker):
   ```
   17 */3 * * * /ruta/al/proyecto/scripts/backup/backup-db.sh >> /var/log/inversiones-lr-backup.log 2>&1
   ```
   Se usa el minuto 17 para no coincidir con otras tareas que arrancan en punto.

## Restaurar

```bash
scripts/backup/restaurar-db.sh /var/backups/inversiones-lr/inversiones_lr_20261118_0317.dump
```
Borra los datos actuales de la base, detiene el backend mientras restaura y lo vuelve a iniciar.
Si la restauración falla, la base queda como estaba (`--single-transaction`).
