import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { getStatement, statementHtmlUrl, statementPdfUrl } from '../../api/statements';
import { formatAmount, formatTimestamp, maskCard } from './StatementListPage';

/**
 * COSTM01C — Statement View (S-21 OnlineStatements, opened from COSTM00C with
 * `S` beside a cycle). A modern screen with no legacy COBOL behind it:
 *   - ?stmtId=... loads GET /api/statements/{stmtId} and shows a summary header
 *     (account, customer, card ****1234, cycle, transactions, total, generated)
 *   - the stored HTML statement is rendered in a fully sandboxed <iframe>
 *     pointed at /api/statements/{stmtId}/html (served under a no-script CSP)
 *   - Download PDF / Download HTML link to /pdf and /html
 *   - a statement that no longer exists (404) shows the API's message, or
 *     NOT_FOUND_MSG, and no preview or downloads
 *   - PF3 -> back to the Statement List, relisting the account that was searched
 *     (?acctId=... as passed by COSTM00C, else the statement's own account)
 */

export const NOT_FOUND_MSG = 'Statement not found. It may no longer exist';
export const NO_STMT_MSG = 'No statement selected. Press F3 to return to the list';
export const DISPLAY_MSG = 'Displaying statement. Download it as PDF or HTML';

const MONO = "'Courier New', Courier, monospace";

const styles = {
  keys: { marginBottom: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: MONO,
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  link: {
    fontFamily: MONO,
    fontSize: '13px',
    padding: '6px 12px',
    color: '#12321a',
    background: '#eef7ee',
    border: '1px solid #2c5d34',
    borderRadius: '2px',
    textDecoration: 'none',
  },
  info: { color: '#0b3d0b', fontFamily: MONO, minHeight: '20px', marginBottom: '4px' },
  error: {
    color: '#b00020',
    fontFamily: MONO,
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  summary: {
    fontFamily: MONO,
    borderCollapse: 'collapse',
    width: '100%',
    maxWidth: '820px',
    marginBottom: '16px',
  },
  th: {
    textAlign: 'left',
    padding: '4px 10px',
    width: '160px',
    color: '#0b3d0b',
    borderBottom: '1px solid #cfe8cf',
    background: '#eef7ee',
  },
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' },
  section: { fontFamily: MONO, color: '#0b3d0b', margin: '12px 0 4px' },
  frame: {
    width: '100%',
    maxWidth: '820px',
    height: '560px',
    border: '1px solid #2c5d34',
    background: '#fff',
  },
};

function pick(...values) {
  return values.find((v) => v !== undefined && v !== null && String(v).trim() !== '');
}

/**
 * Flattens the CMD-4 detail body: statement fields at the top level, with the
 * account/customer header fields either flat or under `account` / `customer`.
 */
export function toSummary(body) {
  const data = body || {};
  const account = data.account || {};
  const customer = data.customer || {};
  const fullName = pick(
    data.customerName,
    customer.name,
    [pick(data.firstName, customer.firstName), pick(data.middleName, customer.middleName),
      pick(data.lastName, customer.lastName)].filter(Boolean).join(' '),
  );
  return {
    stmtId: data.stmtId,
    acctId: pick(data.acctId, account.acctId, account.accountId),
    custId: pick(data.custId, customer.custId),
    customerName: fullName || '',
    cardNum: pick(data.cardNum, account.cardNum),
    cycle: data.cycle,
    txnCount: data.txnCount,
    totalAmount: data.totalAmount,
    currBal: pick(data.currBal, account.currBal),
    creditLimit: pick(data.creditLimit, account.creditLimit),
    generatedTs: data.generatedTs,
  };
}

function summaryRows(s) {
  const rows = [
    ['Account :', s.acctId],
    ['Customer :', [s.custId, s.customerName].filter((v) => v !== undefined && v !== '').join('  ')],
    ['Card :', s.cardNum ? maskCard(s.cardNum) : ''],
    ['Cycle :', s.cycle],
    ['Transactions :', s.txnCount],
    ['Total :', formatAmount(s.totalAmount)],
  ];
  if (s.currBal !== undefined) {
    rows.push(['Current Balance :', formatAmount(s.currBal)]);
  }
  if (s.creditLimit !== undefined) {
    rows.push(['Credit Limit :', formatAmount(s.creditLimit)]);
  }
  rows.push(['Generated :', formatTimestamp(s.generatedTs)]);
  return rows;
}

export default function StatementViewPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const stmtId = (searchParams.get('stmtId') || '').trim();
  const searchedAcct = (searchParams.get('acctId') || '').trim();
  const [summary, setSummary] = useState(null);
  const [info, setInfo] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (!stmtId) {
      setSummary(null);
      setError(NO_STMT_MSG);
      return undefined;
    }
    getStatement(stmtId, { notFoundMessage: NOT_FOUND_MSG })
      .then((body) => {
        if (cancelled) return;
        setSummary(toSummary(body));
        setError('');
        setInfo(DISPLAY_MSG);
      })
      .catch((e) => {
        if (cancelled) return;
        setSummary(null);
        setInfo('');
        setError(e.message || NOT_FOUND_MSG);
      });
    return () => {
      cancelled = true;
    };
  }, [stmtId]);

  const back = useCallback(() => {
    const acctId = searchedAcct || (summary && summary.acctId ? String(summary.acctId) : '');
    navigate(acctId ? `/statements?acctId=${encodeURIComponent(acctId)}` : '/statements');
  }, [navigate, searchedAcct, summary]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        back();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [back]);

  return (
    <Layout tranId="CM00" progName="COSTM01C" title="Statement View" pfKeys="F3=Back">
      <div style={styles.keys}>
        <button type="button" style={styles.button} onClick={back}>F3=Back</button>
        {summary ? (
          <>
            <a style={styles.link} href={statementPdfUrl(stmtId)} download>Download PDF</a>
            <a style={styles.link} href={statementHtmlUrl(stmtId)} download>Download HTML</a>
          </>
        ) : null}
      </div>
      <div style={styles.info} role="status">{info}</div>
      <div style={styles.error} role="alert">{error}</div>

      {summary ? (
        <>
          <table style={styles.summary} aria-label="Statement summary">
            <tbody>
              {summaryRows(summary).map(([label, value]) => (
                <tr key={label}>
                  <th scope="row" style={styles.th}>{label}</th>
                  <td style={styles.td}>{value === undefined || value === null ? '' : String(value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={styles.section}>Statement</div>
          <iframe
            title="Statement preview"
            style={styles.frame}
            src={statementHtmlUrl(stmtId)}
            sandbox=""
          />
        </>
      ) : null}
    </Layout>
  );
}
