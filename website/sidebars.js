/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'intro',
    'quick_start',
    'core_concept',
    {
      type: 'category',
      label: 'Guide',
      collapsed: false,
      items: [
        'guide/actor_model',
        'guide/message_model',
        'guide/stack_model',
        'guide/channel_pipeline',
        'guide/io_model',
        'guide/reactor_model',
        'guide/threading_model',
        'guide/address_model',
        'guide/ioc',
        'guide/serde',
        'guide/slf4a',
      ],
    },
    {
      type: 'category',
      label: 'Modules',
      items: ['module/index', 'module/buffer'],
    },
  ],
};

module.exports = sidebars;
