// @ts-check
const {themes} = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.vsDark;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Otavia',
  tagline: 'A high-performance IO & Actor programming model for Scala 3',
  favicon: 'img/logo.drawio.svg',

  // Set the production url of your site here
  url: 'https://otavia.cc',
  // Set the /<baseUrl>/ pathname under which your site is served
  baseUrl: '/',

  // GitHub pages deployment config.
  organizationName: 'otavia-projects',
  projectName: 'otavia',
  trailingSlash: false,

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'zh-CN'],
    localeConfigs: {
      en: {
        label: 'English',
        direction: 'ltr',
      },
      'zh-CN': {
        label: '简体中文',
        direction: 'ltr',
      },
    },
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/otavia-projects/otavia/tree/main/website/',
        },
        blog: {
          showReadingTime: true,
          editUrl: 'https://github.com/otavia-projects/otavia/tree/main/website/',
          onInlineAuthors: 'ignore',
          onUntruncatedBlogPosts: 'ignore',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  plugins: [],

  // Use Rspack instead of webpack for Node.js 25 compatibility
  future: {
    faster: {
      swcJsLoader: true,
      swcJsMinimizer: true,
      swcHtmlMinimizer: true,
      lightningCssMinimizer: true,
      rspackBundler: true,
      rspackPersistentCache: true,
      mdxCrossCompilerCache: true,
    },
  },

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/otavia-social-card.jpg',
      navbar: {
        title: 'Otavia',
        logo: {
          alt: 'Otavia Logo',
          src: 'img/logo.drawio.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            to: '/api/index.html',
            label: 'API',
            position: 'left',
            target: '_blank',
          },
          { to: '/blog', label: 'Blog', position: 'left' },
          {
            href: 'https://github.com/otavia-projects/otavia',
            label: 'GitHub',
            position: 'right',
          },
          {
            type: 'localeDropdown',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Quick Start',
                to: '/docs/quick_start',
              },
              {
                label: 'Core Concepts',
                to: '/docs/core_concept',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub Issues',
                href: 'https://github.com/otavia-projects/otavia/issues',
              },
              {
                label: 'GitHub Discussions',
                href: 'https://github.com/otavia-projects/otavia/discussions',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Blog',
                to: '/blog',
              },
              {
                label: 'GitHub',
                href: 'https://github.com/otavia-projects/otavia',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Yan Kun and Otavia Project. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java', 'scala'],
        magicComments: [],
      },
    }),
};

module.exports = config;
