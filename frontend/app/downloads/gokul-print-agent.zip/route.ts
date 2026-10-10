import {printInstallerBase64} from '@/lib/generatedPrintInstaller';
export const runtime='nodejs';
export const dynamic='force-static';
/** Public installer contains source/runtime setup only; shop profile and keys remain separate. */
export function GET(){
 const bytes=Buffer.from(printInstallerBase64,'base64');
 return new Response(bytes,{headers:{'Content-Type':'application/zip','Content-Disposition':'attachment; filename="gokul-print-agent.zip"','Content-Length':String(bytes.length),'Cache-Control':'public, max-age=0, must-revalidate','X-Content-Type-Options':'nosniff'}});
}
