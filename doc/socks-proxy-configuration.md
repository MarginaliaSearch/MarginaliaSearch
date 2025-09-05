# SOCKS Proxy Support for Crawlers

This document describes how to configure and use SOCKS proxy support in Marginalia's crawler processes to distribute IP footprint across multiple remote servers.

## Overview

The SOCKS proxy feature allows crawlers to route their HTTP requests through SOCKS proxies running on remote servers. This helps distribute the IP footprint and avoid rate limiting or blocking from target websites.

## Configuration

SOCKS proxy support is configured via system properties. The following properties are available:

### Basic Configuration

- `crawler.socksProxy.enabled` (default: `false`)
  - Set to `true` to enable SOCKS proxy support

- `crawler.socksProxy.list` (default: empty)
  - Comma-separated list of SOCKS proxy servers
  - Format: `host:port` or `host:port:username:password`
  - Example: `1.1.1.5:1080,1.1.1.10:1080,1.1.1.15:1080`

- `crawler.socksProxy.strategy` (default: `ROUND_ROBIN`)
  - Proxy selection strategy: `ROUND_ROBIN` or `RANDOM`

### Example Configuration

To enable SOCKS proxy with 3 remote servers using round-robin selection:

```bash
-Dcrawler.socksProxy.enabled=true
-Dcrawler.socksProxy.list=1.1.1.5:1080,1.1.1.10:1080,1.1.1.15:1080
-Dcrawler.socksProxy.strategy=ROUND_ROBIN
```

For authenticated proxies:

```bash
-Dcrawler.socksProxy.enabled=true
-Dcrawler.socksProxy.list=1.1.1.5:1080:user1:pass1,1.1.1.10:1080:user2:pass2,1.1.1.15:1080:user3:pass3
-Dcrawler.socksProxy.strategy=RANDOM
```

## Setting Up SOCKS Proxies

### Using sockd (SOCKS5 daemon)

On each remote server (1.1.1.5, 1.1.1.10, 1.1.1.15), install and configure sockd:

1. Install sockd (varies by OS):
   ```bash
   # Ubuntu/Debian
   sudo apt-get install dante-server
   
   # CentOS/RHEL
   sudo yum install dante
   ```

2. Configure sockd (`/etc/sockd.conf`):
   ```
   # Listen on port 1080
   internal: 0.0.0.0 port = 1080
   
   # Allow connections from your crawler host
   external: eth0
   
   # Authentication (optional)
   user.privileged: root
   user.unprivileged: nobody
   
   # Allow connections
   client pass {
       from: YOUR_CRAWLER_HOST_IP/32 to: 0.0.0.0/0
       log: connect disconnect
   }
   
   # Allow outgoing connections
   pass {
       from: 0.0.0.0/0 to: 0.0.0.0/0
       protocol: tcp udp
       log: connect disconnect
   }
   ```

3. Start sockd:
   ```bash
   sudo systemctl start sockd
   sudo systemctl enable sockd
   ```

### Alternative: Using SSH Tunnels

You can also use SSH tunnels as SOCKS proxies:

```bash
# On your local machine, create SSH tunnels to remote servers
ssh -D 1080 -N user@1.1.1.5
ssh -D 1081 -N user@1.1.1.10  
ssh -D 1082 -N user@1.1.1.15
```

Then configure:
```bash
-Dcrawler.socksProxy.list=localhost:1080,localhost:1081,localhost:1082
```

## Affected Processes

SOCKS proxy support is integrated into the following crawler processes:

1. **Main Crawler** (`crawling-process`)
   - Primary web crawling functionality
   - Uses `HttpFetcherImpl` with SOCKS proxy support

2. **Live Crawler** (`live-crawling-process`)
   - Real-time crawling for live updates
   - Uses `HttpClientProvider` with SOCKS proxy support

3. **Ping Process** (`ping-process`)
   - Domain availability checking
   - Uses `HttpClientProvider` with SOCKS proxy support

4. **New Domain Process** (`new-domain-process`)
   - New domain discovery and validation
   - Uses `HttpClientProvider` with SOCKS proxy support

## Implementation Details

### Proxy Selection Strategies

- **ROUND_ROBIN**: Cycles through proxies in order, ensuring even distribution
- **RANDOM**: Randomly selects a proxy for each request

### Connection Management

- Each HTTP client maintains its own connection pool
- SOCKS proxy connections are established per request
- Connection timeouts and retry logic remain unchanged
- SSL/TLS connections work transparently through SOCKS proxies

### Logging

The system logs proxy selection and configuration:
- Startup logs show enabled proxies and strategy
- Debug logs show which proxy is selected for each request
- Error logs indicate proxy connection failures

## Troubleshooting

### Common Issues

1. **Proxy Connection Failures**
   - Verify proxy servers are running and accessible
   - Check firewall rules allow connections to proxy ports
   - Verify proxy authentication credentials if required

2. **No Traffic Through Proxies**
   - Ensure `crawler.socksProxy.enabled=true`
   - Verify proxy list is correctly formatted
   - Check logs for proxy selection messages

3. **Performance Issues**
   - SOCKS proxies add latency - consider geographic proximity
   - Monitor proxy server resources and bandwidth
   - Adjust connection pool sizes if needed

### Testing Proxy Configuration

You can test your proxy configuration using curl:

```bash
# Test individual proxies
curl --socks5 1.1.1.5:1080 http://httpbin.org/ip
curl --socks5 1.1.1.10:1080 http://httpbin.org/ip
curl --socks5 1.1.1.15:1080 http://httpbin.org/ip

# Test with authentication
curl --socks5 user:pass@1.1.1.5:1080 http://httpbin.org/ip
```

## Security Considerations

- Use authenticated proxies when possible
- Ensure proxy servers are properly secured
- Monitor proxy usage for unusual patterns
- Consider using VPNs or dedicated proxy services for production use
- Rotate proxy credentials regularly

## Performance Impact

- **Latency**: SOCKS proxies add network latency (typically 10-100ms)
- **Bandwidth**: Additional bandwidth usage for proxy connections
- **Reliability**: Proxy failures can cause request failures
- **Throughput**: May reduce overall crawling throughput

Consider these factors when planning your proxy infrastructure and crawling targets.
