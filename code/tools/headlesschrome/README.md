# Headless

This package builds a harness for running a headless browser for annotated DOM exports,
and screenshots, used by [live-capture](../../index/live-capture).

It's based on Jooby and Selenium, and exposes a basic REST API:

```
GET /healthcheck -- tests if healthy
Response: 200 if OK

POST /dom-sample -- capture annotated DOM sample
Headers:
    Authorization: (token)
Body:
    { "url": "https://your-url.example.com" }

POST /screenshot -- capture a screenshot
Headers:
    Authorization: (token)
Body:
    { "url": "https://your-url.example.com" }

POST /kill -- indicates to the server to self-terminate
Headers:
    Authorization: (token)
Body: 
    Not read
  
```


