package com.newnil.cas.oauth2.provider.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.ConsumerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.newnil.cas.oauth2.provider.config.OAuth2ServerConfig.ClientIdsInMemory;
import com.newnil.cas.oauth2.provider.oauth.SparklrUserApprovalHandler;

@Controller
public class AdminController {

	@Autowired
	// N.B. the @Qualifier here should not be necessary (gh-298) but lots of users report
	// needing it.
	@Qualifier("consumerTokenServices")
	private ConsumerTokenServices tokenServices;

	@Autowired
	private ClientDetailsService clientDetails;

	@Autowired
	private TokenStore tokenStore;

	@Autowired
	private SparklrUserApprovalHandler userApprovalHandler;

	@RequestMapping(value = "/oauth/tokens", produces = { "application/json" })
	@ResponseBody
	public ClientsTokensList listAllTokens() {
		ClientsTokensList result = new ClientsTokensList();
		for (String clientId : clientIds) {
			result.put(clientId, enhance(tokenStore.findTokensByClientId(clientId)));
		}
		return result;
	}

	@RequestMapping("/oauth/tokens")
	public String tokenAdminPage(Model model) {
		ClientsTokensList result = listAllTokens();
		model.addAttribute("tokensList", result);
		return "admin";
	}

	@RequestMapping(value = "/oauth/tokens/revoke", method = RequestMethod.POST)
	public String revokeToken(@RequestParam("user") String user,
			@RequestParam("token") String token, Principal principal) throws Exception {
		checkResourceOwner(user, principal);
		if (tokenServices.revokeToken(token)) {
			return "redirect:/oauth/tokens?revoke-success";
		}
		else {
			return "redirect:/oauth/tokens?revoke-empty";
		}
	}

	private void checkResourceOwner(String user, Principal principal) {
		if (principal instanceof OAuth2Authentication) {
			OAuth2Authentication authentication = (OAuth2Authentication) principal;
			if (!authentication.isClientOnly() && !user.equals(principal.getName())) {
				throw new AccessDeniedException(
						String.format("User '%s' cannot obtain tokens for user '%s'",
								principal.getName(), user));
			}
		}
	}

	private Collection<OAuth2AccessToken> enhance(Collection<OAuth2AccessToken> tokens) {
		Collection<OAuth2AccessToken> result = new ArrayList<OAuth2AccessToken>();
		for (OAuth2AccessToken prototype : tokens) {
			DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken(prototype);
			OAuth2Authentication authentication = tokenStore.readAuthentication(token);
			if (authentication == null) {
				continue;
			}
			String userName = authentication.getName();
			if (StringUtils.isEmpty(userName)) {
				userName = "Unknown";
			}
			Map<String, Object> map = new HashMap<String, Object>(
					token.getAdditionalInformation());
			map.put("user_name", userName);
			token.setAdditionalInformation(map);
			result.add(token);
		}
		return result;
	}

	@Autowired
	private ClientIdsInMemory clientIds;

	@SuppressWarnings("serial")
	public static class ClientsTokensList
			extends HashMap<String, Collection<OAuth2AccessToken>> {

	}

}
