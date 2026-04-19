import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Translate, {translate} from '@docusaurus/Translate';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import styles from './index.module.css';
import CodeBlock from '@theme/CodeBlock';

const codeSnippet = `// Define messages with type safety
case class Ping(id: Int) extends Ask[Pong]
case class Pong(id: Int) extends Reply

// Actors handle messages asynchronously
class PingActor(pong: Address[Ping])
  extends StateActor[Start] {
  def resumeNotice(stack) = stack.state match {
    case StartState =>
      pong.ask(Ping(1))
      stack.suspend(FutureState())
    case s: FutureState[Pong] =>
      println("Pong received: " + s.future.getNow.toString)
      stack.return()
  }
}

// Build and run
val system = ActorSystem()
val pong = system.buildActor(() => PongActor())
val ping = system.buildActor(() => PingActor(pong))
ping.notice(Start(1))`;

function HomepageHeader() {
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className={styles.heroBackground}>
        <div className={styles.heroGradient} />
      </div>
      <div className="container">
        <div className={styles.heroGrid}>
          <div className={styles.heroContent}>
            <div className={styles.badgeRow}>
              <span className={styles.badge}>JDK 17+</span>
              <span className={styles.badge}>Scala 3.3+</span>
              <span className={styles.badge}>High Performance</span>
            </div>
            <h1 className={styles.heroTitle}>
              <Translate>Otavia</Translate>
            </h1>
            <p className={styles.heroSubtitle}>
              <Translate description="hero subtitle">
                A high-performance IO & Actor programming model for Scala 3
              </Translate>
            </p>
            <p className={styles.heroDescription}>
              <Translate description="hero description">
                Build scalable concurrent applications with zero-cost abstractions,
                type-safe message passing, and a powerful Netty-based IO stack.
              </Translate>
            </p>
            <div className={styles.heroButtons}>
              <Link
                className="button button--primary button--lg"
                to="/docs/quick_start">
                <Translate>Quick Start</Translate>
              </Link>
              <Link
                className="button button--outline button--lg"
                to="/docs/core_concept">
                <Translate>Core Concepts</Translate>
              </Link>
            </div>
          </div>
          <div className={styles.heroCode}>
            <div className={styles.codeCard}>
              <div className={styles.codeHeader}>
                <span className={styles.codeDot} />
                <span className={styles.codeDot} />
                <span className={styles.codeDot} />
                <span className={styles.codeTitle}>PingPong.scala</span>
              </div>
              <CodeBlock language="scala">{codeSnippet}</CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}

function ComparisonSection() {
  return (
    <section className={styles.comparison}>
      <div className="container">
        <h2 className={styles.sectionTitle}>
          <Translate description="comparison section title">
            Why Otavia?
          </Translate>
        </h2>
        <div className={styles.comparisonGrid}>
          <div className={styles.comparisonCard}>
            <h3 className={styles.comparisonOld}>
              <Translate description="traditional threading">Traditional Threading</Translate>
            </h3>
            <ul className={styles.comparisonList}>
              <li>
                <span className={styles.cross}>✗</span>
                <Translate description="thread problem 1">Complex thread management</Translate>
              </li>
              <li>
                <span className={styles.cross}>✗</span>
                <Translate description="thread problem 2">Race conditions & deadlocks</Translate>
              </li>
              <li>
                <span className={styles.cross}>✗</span>
                <Translate description="thread problem 3">Blocking kills performance</Translate>
              </li>
              <li>
                <span className={styles.cross}>✗</span>
                <Translate description="thread problem 4">Hard to reason about</Translate>
              </li>
            </ul>
          </div>
          <div className={clsx(styles.comparisonCard, styles.comparisonHighlight)}>
            <h3 className={styles.comparisonNew}>
              <Translate description="otavia way">With Otavia</Translate>
            </h3>
            <ul className={styles.comparisonList}>
              <li>
                <span className={styles.check}>✓</span>
                <Translate description="otavia benefit 1">Single-threaded actors</Translate>
              </li>
              <li>
                <span className={styles.check}>✓</span>
                <Translate description="otavia benefit 2">No locks needed</Translate>
              </li>
              <li>
                <span className={styles.check}>✓</span>
                <Translate description="otavia benefit 3">Zero-cost async/await</Translate>
              </li>
              <li>
                <span className={styles.check}>✓</span>
                <Translate description="otavia benefit 4">Compile-time type safety</Translate>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <Layout
      title={translate({
        message: 'Welcome to Otavia',
        description: 'Homepage',
      })}
      description="A high-performance IO & Actor programming model for Scala 3">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <ComparisonSection />
        <section className={styles.cta}>
          <div className="container">
            <h2 className={styles.ctaTitle}>
              <Translate description="cta title">Ready to get started?</Translate>
            </h2>
            <p className={styles.ctaDescription}>
              <Translate description="cta description">
                Check out our documentation to learn more about Otavia and start building your first actor-based application.
              </Translate>
            </p>
            <div className={styles.ctaButtons}>
              <Link
                className="button button--primary button--lg"
                to="/docs/quick_start">
                <Translate>Quick Start</Translate>
              </Link>
              <Link
                className="button button--outline button--lg"
                to="/docs/intro">
                <Translate>Read the Docs</Translate>
              </Link>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
