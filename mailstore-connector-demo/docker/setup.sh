SETUP(1)

NAME
    setup - 'docker-mailserver' Administration & Configuration CLI

SYNOPSIS
    setup [ OPTIONS... ] COMMAND [ help | ARGUMENTS... ]

    COMMAND := { email | alias | quota | dovecot-master | config | relay | debug } SUBCOMMAND

DESCRIPTION
    This is the main administration command that you use for all your interactions with
    'docker-mailserver'. Initial setup, configuration, and much more is done with this CLI tool.

    Most subcommands can provide additional information and examples by appending 'help'.
    For example: 'setup email add help'

[SUB]COMMANDS
    COMMAND email :=
        setup email add <EMAIL ADDRESS> [<PASSWORD>]
        setup email update <EMAIL ADDRESS> [<PASSWORD>]
        setup email del [ OPTIONS... ] <EMAIL ADDRESS> [ <EMAIL ADDRESS>... ]
        setup email restrict <add|del|list> <send|receive> [<EMAIL ADDRESS>]
        setup email list

    COMMAND alias :=
        setup alias add <EMAIL ADDRESS> <RECIPIENT>
        setup alias del <EMAIL ADDRESS> <RECIPIENT>
        setup alias list

    COMMAND quota :=
        setup quota set <EMAIL ADDRESS> [<QUOTA>]
        setup quota del <EMAIL ADDRESS>

    COMMAND dovecot-master :=
        setup dovecot-master add <USERNAME> [<PASSWORD>]
        setup dovecot-master update <USERNAME> [<PASSWORD>]
        setup dovecot-master del [ OPTIONS... ] <USERNAME> [ <USERNAME>... ]
        setup dovecot-master list

    COMMAND config :=
        setup config dkim [ ARGUMENTS... ]

    COMMAND relay :=
        setup relay add-auth <DOMAIN> <USERNAME> [<PASSWORD>]
        setup relay add-domain <DOMAIN> <HOST> [<PORT>]
        setup relay exclude-domain <DOMAIN>

    COMMAND fail2ban :=
        setup fail2ban
        setup fail2ban ban <IP>
        setup fail2ban unban <IP>
        setup fail2ban log
        setup fail2ban status

    COMMAND debug :=
        setup debug fetchmail
        setup debug login <COMMANDS>
        setup debug show-mail-logs

EXAMPLES
    setup email add test@example.com
        Add the email account test@example.com. You will be prompted
        to input a password afterwards since no password was supplied.

    setup config dkim keysize 2048 domain 'example.com,not-example.com'
        Creates keys of length 2048 for the domains in comma-seperated list.
        This is necessary when using LDAP as the required domains cannot be inferred.

    setup config dkim help
        This will provide you with a detailed explanation on how to use the
        config dkim command, showing what arguments can be passed and what they do.

