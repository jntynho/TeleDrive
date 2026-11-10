#!/usr/bin/env python3
"""Inspect real exported schema plans using 100k synthetic rows (desktop SQLite, not a phone/FPS benchmark)."""
import json,sqlite3,time
from pathlib import Path
schema=json.loads((Path(__file__).resolve().parents[1]/'shared/schemas/com.infinity.drive.data.local.database.DriveInfinityDatabase/13.json').read_text())['database']['entities']
c=sqlite3.connect(':memory:');c.execute('PRAGMA foreign_keys=OFF')
for e in schema:
 t=e['tableName'];c.execute(e['createSql'].replace('${TABLE_NAME}',t))
 for index in e.get('indices',[]):c.execute(index['createSql'].replace('${TABLE_NAME}',t))
f=next(e for e in schema if e['tableName']=='files')
fields={v['columnName']:(0 if v['affinity']=='INTEGER' else '') for v in f['fields'] if v.get('notNull')}
fields.update(id='',name='',mimeType='image/jpeg',category='IMAGE',backupState='BACKED_UP',chatId=1)
cols=list(fields);sql='INSERT INTO files('+','.join(cols)+') VALUES('+','.join('?' for _ in cols)+')'
rows=[]
for i in range(100000):
 v=dict(fields);v.update(id=f'{i:06}',name=f'Photo-{i}.jpg',chatId=i%3+1,category='VIDEO' if i%5==0 else 'IMAGE',modifiedAt=i)
 rows.append(tuple(v[k] for k in cols))
c.executemany(sql,rows);c.commit();c.execute('ANALYZE')
base="SELECT * FROM files WHERE trashedAt IS NULL AND chatId=? AND category IN ({}) AND isHidden=0 AND isArchived=0 ORDER BY modifiedAt DESC,id ASC LIMIT 40"
for label,args in [('Photos',(1,'IMAGE')),('All media',(1,'IMAGE','VIDEO'))]:
 q=base.format(','.join('?' for _ in args[1:]));plan=c.execute('EXPLAIN QUERY PLAN '+q,args).fetchall();start=time.perf_counter()
 for _ in range(10):c.execute(q,args).fetchall()
 print(label,'plan:',plan,'mean_ms:',round((time.perf_counter()-start)*1000/10,2))
