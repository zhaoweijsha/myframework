package com.myframework.core.token;

import com.myframework.core.filter.RequestFilter;
import com.myframework.core.token.exception.TokenCode;
import com.myframework.core.token.exception.TokenException;
import com.myframework.util.CookieUtil;
import com.myframework.util.SpringContextUtil;
import com.myframework.util.StringUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by zw on 2017/7/19.
 */
public class TokenManager {

    private final static Logger logger = LoggerFactory.getLogger(TokenManager.class);

    private static JwtTokenUtil jwtTokenUtil = null;

    public static boolean isTokenEnable() {
        return getJwtTokenUtil().isTokenEnable();
    }

    public static String getTokenFromRequest(HttpServletRequest request) {
        String token = request.getHeader(getJwtTokenUtil().getTokenHeader());
        //header 取不到的时候从参数里取token字段
        if (StringUtil.isNullOrEmpty(token)) {
            token = request.getParameter(getJwtTokenUtil().getTokenHeader());
        }
        //header 取不到的时候从token里取token字段
        if (StringUtil.isNullOrEmpty(token)) {

            Cookie tokenCookie = CookieUtil.getCookie(request.getCookies(), getJwtTokenUtil().getTokenHeader());
            if (tokenCookie != null) {
                token = tokenCookie.getValue();
            }
        }

        if (!StringUtil.isNullOrEmpty(token) && token.contains(getJwtTokenUtil().getTokenPrefix())) {
            token = token.substring(getJwtTokenUtil().getTokenPrefix().length() + 1);
        }

        return token;
    }

    public static boolean isTokenExpired(String token) {
        return getJwtTokenUtil().isTokenExpired(token);
    }

    public static Pair<JwtInfo, JwtInfo> createAuthenticationToken(JwtSubjectInfo jwtSubjectInfo) throws TokenException {
        // Reload password post-security so we can generate token
        JwtTokenUtil jwtTokenUtil = SpringContextUtil.getBean(JwtTokenUtil.class);
        // 将当前token信息保存到session
        RequestFilter.getSession().setAttribute(TokenConstant.SESSION_SUBJECT_INFO, jwtSubjectInfo);
        JwtInfo tokenInfo = jwtTokenUtil.generateToken(jwtSubjectInfo);
        JwtInfo refreshTokenInfo = jwtTokenUtil.refreshToken(tokenInfo.getToken(), true);
        // Return the token
        return Pair.of(tokenInfo, refreshTokenInfo);
    }

    public static Pair<JwtInfo, JwtInfo> refreshAndGetAuthenticationToken(String refreshToken) throws TokenException {
        JwtInfo tokenInfo = getJwtTokenUtil().refreshToken(refreshToken, false);
        JwtInfo refreshTokenInfo = getJwtTokenUtil().refreshToken(refreshToken, true);
        return Pair.of(tokenInfo, refreshTokenInfo);
    }

    public static boolean validateToken(String token) throws TokenException {
        if (StringUtil.isNullOrEmpty(token)) {
            throw new TokenException("token not exist!");
        }
        JwtSubjectInfo jwtSubjectInfo = null;
        if (RequestFilter.getSession() != null && RequestFilter.getSession().getAttribute(TokenConstant.SESSION_SUBJECT_INFO) != null) {
            jwtSubjectInfo = (JwtSubjectInfo) RequestFilter.getSession().getAttribute(TokenConstant.SESSION_SUBJECT_INFO);
        }
        boolean valid = true;
        if (isTokenExpired(token)) {
            //已经过期的token，判断能否刷新并继续用一段时间
            if (getJwtTokenUtil().canTokenBeRefreshed(token, jwtSubjectInfo != null ? jwtSubjectInfo.getLastPasswordReset() : null)) {
                valid = getJwtTokenUtil().validateToken(token, jwtSubjectInfo);
            }
        } else {
            //未过期检验token合法性
            valid = getJwtTokenUtil().validateToken(token, jwtSubjectInfo);
        }
        checkSessionExist(token);
        return valid;
    }

    public static boolean validateTokenAuth(String withTokenAuth, HttpServletRequest request) throws TokenException {
        String token = getTokenFromRequest(request);
        if (StringUtil.isNullOrEmpty(token)) {
            throw new TokenException("token not exist!");
        }
        String grantedAuths = getJwtTokenUtil().getGrantedAuthsFromToken(token);
        if (StringUtil.isNullOrEmpty(grantedAuths)) {
            throw new TokenException("token auth failed");
        }
        Collection<String> granted = Arrays.asList(grantedAuths.split(","));
        Collection<String> auths = Arrays.asList(withTokenAuth.split(","));
        for (String auth : auths) {
            if (!granted.contains(auth)) {
                throw new TokenException("token auth failed");
            }
        }
        return true;
    }

    /**
     * 判断session是否存在，如果session不在，需要重新登录;这个主要用在 1. 修改密码，清空session就可以让用户强制重新登录 2.
     * 修改了企业的token超时时间，也可以通过这个方法让用户重新登录，更新超时配置 3. 在web 2处登录，第二个登录的会把第一个t下线
     *
     * @param token
     */
    public static void checkSessionExist(String token) throws TokenException {

        String obj = getJwtTokenUtil().getTokenFromSession(token);

        if (obj == null) {
            throw new TokenException(TokenCode.IEAGLLE_TOKEN_4_SESSION_EXPIRE.getCode());
        }

        // 账号在其他地方登录，导致redis中查到的当前状态的token和传入的token不一致
        if (!String.valueOf(obj).equals(token)) {
            logger.info("TokenManager checkSessionExist failed,[sessionToken] = {}, [token] is:{}", obj, token);
            throw new TokenException(TokenCode.RELOGIN_BY_LOGIN_IN_OTHER_PLACE.getCode());
        }

    }

    public static JwtTokenUtil getJwtTokenUtil() {
        if (jwtTokenUtil == null) {
            jwtTokenUtil = (JwtTokenUtil) SpringContextUtil.getBean(JwtTokenUtil.class);
        }
        return jwtTokenUtil;
    }
}
