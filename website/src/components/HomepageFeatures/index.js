import clsx from 'clsx';
import Heading from '@theme/Heading';
import Translate from '@docusaurus/Translate';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: string;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Zero-Cost Ask Pattern',
    Svg: require('@site/static/img/concurrency.svg').default,
    description: 'Send ask messages and get replies like calling a method. No Future overhead, no allocation on hot paths.',
  },
  {
    title: 'Type-Safe Messaging',
    Svg: require('@site/static/img/typesafe.svg').default,
    description: 'Message passing between actors is type-safe at compile time. Catch errors before runtime.',
  },
  {
    title: 'Stack Coroutines',
    Svg: require('@site/static/img/async.svg').default,
    description: 'async/await syntax via CPS using Scala 3 metaprogramming. Sequential code, async performance.',
  },
  {
    title: 'No More Locks',
    Svg: require('@site/static/img/resilient.svg').default,
    description: 'Single-threaded actors mean no race conditions, no deadlocks, no thread safety headaches.',
  },
  {
    title: 'IOC for Actors',
    Svg: require('@site/static/img/ioc.svg').default,
    description: 'ActorSystem is also an IOC container. Type-safely autowire dependent actors at compile time.',
  },
  {
    title: 'Netty IO Stack',
    Svg: require('@site/static/img/network.svg').default,
    description: 'Ported from Netty with AIO and file channel support. Leverage the rich Netty codec ecosystem.',
  },
  {
    title: 'Object Pooling',
    Svg: require('@site/static/img/performance.svg').default,
    description: 'Zero-cost abstractions with object pooling for hot paths. Millions of actors, billions of messages.',
  },
  {
    title: 'Zero Dependencies',
    Svg: require('@site/static/img/ecosystem.svg').default,
    description: 'Core modules depend on nothing but Scala and JDK. Pure, portable, predictable.',
  },
];

function FeatureCard({title, Svg, description}: FeatureItem) {
  return (
    <div className={styles.featureCard}>
      <div className={styles.featureIconWrapper}>
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <h3 className={styles.featureTitle}>
        <Translate description={`feature: ${title}`}>{title}</Translate>
      </h3>
      <p className={styles.featureDescription}>
        <Translate description={`feature desc: ${title}`}>{description}</Translate>
      </p>
    </div>
  );
}

export default function HomepageFeatures(): JSX.Element {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <Translate description="features section title">Why Otavia?</Translate>
          </h2>
          <p className={styles.sectionSubtitle}>
            <Translate description="features subtitle">
              Built for high-performance Scala with unique features you won't find elsewhere
            </Translate>
          </p>
        </div>
        <div className={styles.featureGrid}>
          {FeatureList.map((props, idx) => (
            <FeatureCard key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
