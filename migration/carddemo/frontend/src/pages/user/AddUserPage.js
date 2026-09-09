import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { addUser } from '../../api/users';

/**
 * CU01 — Add User (COUSR01C / map COUSR1A). Field order and labels are the map's
 * (bms:80-149); the edits and their messages are the program's:
 *   - ENTER -> validate First Name, Last Name, User ID, Password, User Type in
 *     that order, then WRITE (FR-UA-3, FR-UA-4, cbl:115-160)
 *   - a duplicate key -> "User ID already exist..." (FR-UA-5, cbl:260-266)
 *   - success -> the green "User <id> has been added ..." and a cleared screen
 *     (FR-UA-4, cbl:250-259)
 *   - PF3 -> COADM01C (FR-UA-7); PF4 -> clear (FR-UA-8)
 *   - PF12 is advertised on the map but has no branch in the program, so it is
 *     the WHEN OTHER arm: "Invalid key pressed. Please see below..."
 *     (FR-UA-9, quirk Q8, cbl:97-101)
 */

const INVALID_KEY_MSG = 'Invalid key pressed. Please see below...';
const PF_KEYS = 'ENTER=Add User  F3=Back  F4=Clear  F12=Exit';

const EMPTY = { firstName: '', lastName: '', userId: '', password: '', userType: '' };

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  row: { marginBottom: '8px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
    textTransform: 'uppercase',
  },
  hint: { marginLeft: '10px', color: '#12321a' },
  keys: { marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  error: {
    color: '#b00020',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  success: {
    color: '#0b6b18',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

export default function AddUserPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const set = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const submit = useCallback(async (e) => {
    if (e) {
      e.preventDefault();
    }
    setError('');
    setSuccess('');
    try {
      const result = await addUser(form);
      // CLEAR-CURRENT-SCREEN after a successful WRITE (cbl:255-258).
      setForm(EMPTY);
      setSuccess(result.message);
    } catch (err) {
      setError(err.message);
    }
  }, [form]);

  // FR-UA-8: PF4 wipes the input fields and the message line.
  const clear = useCallback(() => {
    setForm(EMPTY);
    setError('');
    setSuccess('');
  }, []);

  const backToAdminMenu = useCallback(() => navigate('/admin'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToAdminMenu();
      } else if (e.key === 'F4') {
        e.preventDefault();
        clear();
      } else if (['F5', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11', 'F12'].includes(e.key)) {
        e.preventDefault();
        setSuccess('');
        setError(INVALID_KEY_MSG);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToAdminMenu, clear]);

  return (
    <Layout tranId="CU01" progName="COUSR01C" title="Add User" pfKeys={PF_KEYS}>
      <form style={styles.form} onSubmit={submit}>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="firstName">First Name:</label>
          <input id="firstName" style={styles.input} maxLength={20}
                 value={form.firstName} onChange={set('firstName')} autoFocus />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="lastName">Last Name:</label>
          <input id="lastName" style={styles.input} maxLength={20}
                 value={form.lastName} onChange={set('lastName')} />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userId">User ID:</label>
          <input id="userId" style={styles.input} maxLength={8}
                 value={form.userId} onChange={set('userId')} />
          <span style={styles.hint}>(8 Char)</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="password">Password:</label>
          {/* The map field is DRK (non-display); a password input is the modern
              equivalent of an unreadable 3270 field. */}
          <input id="password" type="password" style={styles.input} maxLength={8}
                 value={form.password} onChange={set('password')} />
          <span style={styles.hint}>(8 Char)</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userType">User Type: </label>
          <input id="userType" style={{ ...styles.input, width: '40px' }} maxLength={1}
                 value={form.userType} onChange={set('userType')} />
          <span style={styles.hint}>(A=Admin, U=User)</span>
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Add User</button>
          <button type="button" style={styles.button} onClick={backToAdminMenu}>F3 — Back</button>
          <button type="button" style={styles.button} onClick={clear}>F4 — Clear</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.success} role="status">{success}</div>
    </Layout>
  );
}
