package cn.thefronts.oauth.config;

import cn.thefronts.common.config.DefaultPasswordConfig;
import cn.thefronts.common.constant.SecurityConstants;
import cn.thefronts.common.properties.TenantProperties;
import cn.thefronts.oauth.filter.LoginProcessSetTenantFilter;
import cn.thefronts.oauth.mobile.MobileAuthenticationSecurityConfig;
import cn.thefronts.oauth.openid.OpenIdAuthenticationSecurityConfig;
import cn.thefronts.oauth.password.PasswordAuthenticationProvider;
import cn.thefronts.oauth.service.impl.UserDetailServiceFactory;
import cn.thefronts.oauth.tenant.TenantAuthenticationSecurityConfig;
import cn.thefronts.oauth.tenant.TenantUsernamePasswordAuthenticationFilter;
import cn.thefronts.oauth2.common.token.CustomWebAuthenticationDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * spring security??????
 * ???WebSecurityConfigurerAdapter?????????oauth??????????????????
 *
 * @author thefronts
 * @date 2023/2/22
 */
@Configuration
@Import(DefaultPasswordConfig.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private AuthenticationSuccessHandler authenticationSuccessHandler;

	@Autowired(required = false)
	private AuthenticationEntryPoint authenticationEntryPoint;

	@Resource
	private UserDetailServiceFactory userDetailsServiceFactory;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Resource
	private LogoutHandler logoutHandler;

	@Resource
	private LogoutSuccessHandler logoutSuccessHandler;

	@Autowired
	private OpenIdAuthenticationSecurityConfig openIdAuthenticationSecurityConfig;

	@Autowired
	private MobileAuthenticationSecurityConfig mobileAuthenticationSecurityConfig;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private TenantAuthenticationSecurityConfig tenantAuthenticationSecurityConfig;

	@Autowired
	private TenantProperties tenantProperties;

	@Autowired
	private AuthenticationDetailsSource<HttpServletRequest, CustomWebAuthenticationDetails> authenticationDetailsSource;

	/**
	 * ?????????????????????????????????????????????SpringBoot?????????????????????AuthenticationManager,???????????????????????????
	 * @return ??????????????????
	 */
	@Bean
    @Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Bean
	public TenantUsernamePasswordAuthenticationFilter tenantAuthenticationFilter(AuthenticationManager authenticationManager) {
		TenantUsernamePasswordAuthenticationFilter filter = new TenantUsernamePasswordAuthenticationFilter();
		filter.setAuthenticationManager(authenticationManager);
		filter.setFilterProcessesUrl(SecurityConstants.OAUTH_LOGIN_PRO_URL);
		filter.setAuthenticationSuccessHandler(authenticationSuccessHandler);
		filter.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler(SecurityConstants.LOGIN_FAILURE_PAGE));
		filter.setAuthenticationDetailsSource(authenticationDetailsSource);
		return filter;
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.authorizeRequests()
					.anyRequest()
					//?????????????????????basic??????
                    .permitAll()
                    .and()
				.logout()
					.logoutUrl(SecurityConstants.LOGOUT_URL)
					.logoutSuccessHandler(logoutSuccessHandler)
					.addLogoutHandler(logoutHandler)
					.clearAuthentication(true)
					.and()
                .apply(openIdAuthenticationSecurityConfig)
                    .and()
				.apply(mobileAuthenticationSecurityConfig)
					.and()
				.addFilterBefore(new LoginProcessSetTenantFilter(), UsernamePasswordAuthenticationFilter.class)
                .csrf().disable()
				// ????????????????????????iframe?????????
				.headers().frameOptions().disable().cacheControl();

		if (tenantProperties.getEnable()) {
			//????????????????????????????????????????????????
			http.formLogin()
					.loginPage(SecurityConstants.LOGIN_PAGE)
						.and()
					.addFilterAt(tenantAuthenticationFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class)
					.apply(tenantAuthenticationSecurityConfig);
		} else {
			http.formLogin()
					.loginPage(SecurityConstants.LOGIN_PAGE)
					.loginProcessingUrl(SecurityConstants.OAUTH_LOGIN_PRO_URL)
					.successHandler(authenticationSuccessHandler)
					.authenticationDetailsSource(authenticationDetailsSource);
		}


		// ???????????? ??????????????????session,????????????????????????
		if (authenticationEntryPoint != null) {
			http.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint);
			http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
		} else {
			// ????????????????????????????????????session???????????????????????????????????????oauth2?????????
			http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
		}
	}

	/**
	 * ??????????????????
	 */
	@Override
	public void configure(AuthenticationManagerBuilder auth) {
		PasswordAuthenticationProvider provider = new PasswordAuthenticationProvider();
		provider.setPasswordEncoder(passwordEncoder);
		provider.setUserDetailsServiceFactory(userDetailsServiceFactory);
		auth.authenticationProvider(provider);
	}
	/*public void globalUserDetails(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
	}*/
}
