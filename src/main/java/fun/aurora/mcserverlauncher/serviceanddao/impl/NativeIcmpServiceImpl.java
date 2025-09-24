package fun.aurora.mcserverlauncher.serviceanddao.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Slf4j
@Service
public class NativeIcmpServiceImpl {
     //Java内置的isReachable方法
    public Integer ping(String ip) {
        try {
            log.debug("Attempting ICMP ping for: {}", ip);

            if (isLocalAddress(ip)) {
                log.debug("Local address: {}, ping time: 0ms", ip);
                return 0;
            }

            InetAddress address = InetAddress.getByName(ip);

            long startTime = System.nanoTime();
            boolean reachable = address.isReachable(5000); //5秒超时
            long endTime = System.nanoTime();

            if (reachable) {
                long pingTimeNs = endTime - startTime;
                int pingTimeMs = (int) (pingTimeNs / 1_000_000);
                log.info("ICMP ping successful for {}: {}ms", ip, pingTimeMs);
                return pingTimeMs;
            } else {
                log.debug("ICMP ping failed for: {} (unreachable)", ip);
                return null;
            }

        } catch (Exception e) {
            log.debug("ICMP ping error for {}: {}", ip, e.getMessage());
            return null;
        }
    }

    private boolean isLocalAddress(String ip) {
        return ip == null ||
                ip.equals("localhost") ||
                ip.startsWith("127.") ||
                ip.equals("::1");
    }
}