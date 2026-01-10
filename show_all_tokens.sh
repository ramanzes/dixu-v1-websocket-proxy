#!/bin/bash
sqlite3 /opt/websocket-proxy/dixu-v1-websocket-proxy/mydb.sqlite   "SELECT id, device_id, substr(token, 1, 120) as token FROM device_tokens;"
