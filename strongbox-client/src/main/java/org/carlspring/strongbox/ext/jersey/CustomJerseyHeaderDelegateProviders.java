package org.carlspring.strongbox.ext.jersey;

import javax.inject.Singleton;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.message.internal.*;
import org.glassfish.jersey.spi.HeaderDelegateProvider;

/**
 * @author Przemyslaw Fusik
 *
 * @see MessagingBinders.HeaderDelegateProviders
 */
public class CustomJerseyHeaderDelegateProviders
        extends AbstractBinder
{

    @Override
    protected void configure()
    {
        bind(CacheControlProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(CookieProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(DateProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(EntityTagProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(LinkProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(LocaleProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(CustomJerseyMediaTypeProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(NewCookieProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(StringHeaderProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
        bind(UriProvider.class).to(HeaderDelegateProvider.class).in(Singleton.class);
    }
}
