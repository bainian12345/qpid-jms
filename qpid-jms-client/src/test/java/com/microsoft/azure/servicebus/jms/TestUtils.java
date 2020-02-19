package com.microsoft.azure.servicebus.jms;

import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;

public class TestUtils {
    static final String CONNECTION_STRING = "Endpoint=sb://contoso.servicebus.onebox.windows-int.net/;SharedAccessKeyName=DefaultNamespaceSasAllKeyName;SharedAccessKey=8864/auVd3qDC75iTjBL1GJ4D2oXC6bIttRd0jzDZ+g=";
    static final String SEND_ONLY_CONNECTION_STRING = "Endpoint=sb://contoso.servicebus.onebox.windows-int.net/;SharedAccessKeyName=DefaultNamespaceSasSendOnlyKeyName;SharedAccessKey=qsAX1pY6stJyCmNgLBtJgAqXUHzXxFaEP6GDAgMcAQM=";
    static final String LISTEN_ONLY_CONNECTION_STRING = "Endpoint=sb://contoso.servicebus.onebox.windows-int.net/;SharedAccessKeyName=DefaultNamespaceSasListenOnlyKeyName;SharedAccessKey=LzVjg3rfnisLjR7clJ3itnJ7OCgS7JphVLsMkCV5vqM=";
    static final ConnectionStringBuilder CONNECTION_STRING_BUILDER;
    static final ConnectionStringBuilder SEND_ONLY_CONNECTION_STRING_BUILDER;
    static final ConnectionStringBuilder LISTEN_ONLY_CONNECTION_STRING_BUILDER;
    static final ConnectionStringBuilder BAD_CONNECTION_STRING_BUILDER;
    static final ServiceBusJmsConnectionFactorySettings CONNECTION_SETTINGS;

    static {
        CONNECTION_STRING_BUILDER = new ConnectionStringBuilder(CONNECTION_STRING);
        SEND_ONLY_CONNECTION_STRING_BUILDER = new ConnectionStringBuilder(SEND_ONLY_CONNECTION_STRING);
        LISTEN_ONLY_CONNECTION_STRING_BUILDER = new ConnectionStringBuilder(LISTEN_ONLY_CONNECTION_STRING);
        String invalidSasConnectionString = String.valueOf(CONNECTION_STRING).replaceAll("(SharedAccessKey=)(.*)(=)", "SharedAccessKey=BadSasKey");
        BAD_CONNECTION_STRING_BUILDER = new ConnectionStringBuilder(invalidSasConnectionString);
        CONNECTION_SETTINGS = new ServiceBusJmsConnectionFactorySettings(120000, true);
    }
}
